package dev.clearbrowser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.os.Environment
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import dev.clearbrowser.core.engine.BrowserHost
import dev.clearbrowser.core.engine.DownloadRequest
import dev.clearbrowser.core.engine.FileRequest
import java.io.File

/**
 * The Activity's half of the engine seam.
 *
 * Everything here needs an Activity and a `ResultLauncher`, which is exactly why
 * it lives here and not in the engine. A plain class rather than an interface
 * on the Activity itself, so the Activity stays a thin shell.
 */
class MainActivityHost(private val activity: ComponentActivity) : BrowserHost {

    /**
     * The in-flight `<input type="file">` request.
     *
     * Held rather than passed around because a second request can arrive while
     * the first picker is open; the first one has to be answered before it can
     * be dropped, or that input element stays wedged forever.
     */
    private var pendingFile: ((List<Uri>?) -> Unit)? = null

    /**
     * The file a capture is being written into, if one is in flight.
     *
     * Held so the URI can be handed to the page on success and so the file can
     * be deleted on every other outcome. Without this an abandoned capture is
     * left in the cache, and the user has no way to know it is there.
     */
    private var pendingCapture: File? = null

    private val filePicker = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris = when {
            result.resultCode != android.app.Activity.RESULT_OK -> null
            // A capture returns the URI we supplied, not one the camera app
            // chose, so it is deliberately not read back out of the result.
            pendingCapture != null -> listOfNotNull(
                runCatching { uriForCapture(pendingCapture!!) }.getOrNull(),
            )
            else -> parsePickedUris(result.data)
        }
        // Answer, then clear, so the same callback can never fire twice.
        val answer = pendingFile
        pendingFile = null
        discardCapture(keep = uris != null)
        answer?.invoke(uris)
    }

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        // A pending capture is waiting on this launcher specifically.
        if (pendingCaptureRequest != null) {
            onCameraPermissionSettled(
                granted[android.Manifest.permission.CAMERA] == true,
            )
            return@registerForActivityResult
        }
        val answer = pendingPermissions
        pendingPermissions = null
        answer?.invoke(granted)
    }

    private var pendingPermissions: ((Map<String, Boolean>) -> Unit)? = null

    /**
     * A capture waiting on the CAMERA grant, as opposed to a page's
     * `getUserMedia` request.
     *
     * Kept separate from [pendingPermissions] because the two answers go to
     * different places: one resumes a camera intent, the other settles a
     * `PermissionRequest`. Sharing the slot would let a page's request land in
     * the capture path.
     */
    private var pendingCaptureRequest: ((List<Uri>?) -> Unit)? = null

    override fun requestFile(
        request: FileRequest,
        onResult: (List<Uri>?) -> Unit,
    ): Boolean {
        // Settle any earlier request before replacing it.
        pendingFile?.invoke(null)
        discardCapture(keep = false)

        if (request.isCapture) return startCapture(onResult)

        val mimeTypes = request.acceptMimeTypes.filter { it.contains('/') }
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                // An exact type when the page named one, "*/*" otherwise.
                // Guessing here is what makes a picker reject a valid file.
                type = mimeTypes.firstOrNull() ?: "*/*"
                if (mimeTypes.size > 1) {
                    putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
                }
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.allowsMultiple)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        if (runCatching { intent.resolveActivity(activity.packageManager) }.getOrNull() == null) {
            return false
        }

        pendingFile = onResult
        val launched = runCatching { filePicker.launch(intent) }.isSuccess
        if (!launched) pendingFile = null
        return launched
    }

    /**
     * Launch the camera for `<input type="file" capture>`.
     *
     * Two things here are not optional.
     *
     * **We must hold CAMERA ourselves.** Declaring the permission -- which the
     * browser has to do, so a page can use `getUserMedia` -- makes
     * `ACTION_IMAGE_CAPTURE` throw `SecurityException` on Android 11+ unless the
     * permission has actually been granted. Declaring a permission and never
     * holding it turns capture into a guaranteed crash, so the grant is
     * requested *before* launching rather than assumed.
     *
     * **We must supply `EXTRA_OUTPUT`.** Without it the camera app returns a
     * thumbnail as a `Bitmap` in the result extras. That is too small to upload,
     * and it is not a URI, so nothing a page can consume comes back at all.
     */
    private fun startCapture(onResult: (List<Uri>?) -> Unit): Boolean {
        if (hasCameraPermission()) return launchCapture(onResult)

        // Hold the request until the user answers. The callback supersedes any
        // earlier capture request, which is the same contract the permission
        // flow for pages already follows.
        pendingCaptureRequest?.invoke(null)
        pendingCaptureRequest = onResult
        permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
        return true
    }

    /** Called from the permission callback once CAMERA has been settled. */
    private fun onCameraPermissionSettled(granted: Boolean) {
        val request = pendingCaptureRequest
        pendingCaptureRequest = null
        when {
            request == null -> Unit
            // A refusal is a normal outcome, not an error. The page is told
            // "no file", exactly as it would be if the user cancelled the picker.
            !granted -> request(null)
            // Launching can still fail even with the permission held, so the
            // result is checked rather than assumed.
            !launchCapture(request) -> request(null)
        }
    }

    private fun launchCapture(onResult: (List<Uri>?) -> Unit): Boolean {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        if (runCatching { intent.resolveActivity(activity.packageManager) }.getOrNull() == null) {
            return false
        }

        val target = runCatching { newCaptureFile() }.getOrNull() ?: return false
        val outputUri = runCatching { uriForCapture(target) }.getOrNull()
        if (outputUri == null) {
            runCatching { target.delete() }
            return false
        }

        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, outputUri)
        // The camera app is the writer here, so it needs write access, not just
        // the read that the picker path requires.
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )

        pendingCapture = target
        pendingFile = onResult
        val launched = runCatching { filePicker.launch(intent) }.isSuccess
        if (!launched) {
            pendingFile = null
            discardCapture(keep = false)
        }
        return launched
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun newCaptureFile(): File {
        val dir = File(activity.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        // A timestamp rather than a fixed name, so two captures in one session
        // cannot collide into the same file.
        return File(dir, "capture-${System.currentTimeMillis()}.jpg")
    }

    private fun uriForCapture(file: File): Uri = FileProvider.getUriForFile(
        activity,
        "${activity.packageName}.fileprovider",
        file,
    )

    /**
     * Drop the in-flight capture file, unless it is the one being handed to the
     * page.
     */
    private fun discardCapture(keep: Boolean) {
        val file = pendingCapture ?: return
        pendingCapture = null
        if (!keep) runCatching { file.delete() }
    }

    private fun parsePickedUris(data: Intent?): List<Uri>? {
        if (data == null) return null
        val clip = data.clipData
        if (clip != null) {
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }
        return listOfNotNull(data.data)
    }

    override fun requestPermissions(
        permissions: List<String>,
        onResult: (Map<String, Boolean>) -> Unit,
    ) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onResult(permissions.associateWith { true })
            return
        }
        // A second request supersedes the first; answer it so nothing hangs.
        pendingPermissions?.invoke(emptyMap())
        pendingPermissions = onResult
        permissionLauncher.launch(missing.toTypedArray())
    }

    override fun startDownload(request: DownloadRequest) {
        Log.i(
            "ClearBrowser",
            "download: name=${request.suggestedFilename} mime=${request.mimeType}",
        )
        // Validate the scheme first, and treat every failure as a non-event.
        //
        // `DownloadManager.Request` throws `IllegalArgumentException` for
        // anything that is not http(s), and a page can trigger a download of a
        // `data:`, `blob:`, or `content:` URL by putting `download` on an
        // anchor. Letting that reach the constructor crashes the browser, and a
        // crash is the worst possible answer to "that link didn't work".
        val uri = runCatching { Uri.parse(request.url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in DOWNLOADABLE_SCHEMES) {
            Log.w("ClearBrowser", "Refusing to download non-http(s) URL: ${request.url.take(80)}")
            return
        }

        val manager = activity.getSystemService<DownloadManager>() ?: return
        val filename = sanitize(request.suggestedFilename)

        // The whole build-and-enqueue is guarded, not just enqueue: every step
        // of this API throws on bad input, and none of it is worth a crash.
        runCatching {
            // The setters return a nullable Request, so each call is a separate
            // statement rather than a chain.
            val spec = DownloadManager.Request(uri)
            spec.setTitle(filename)
            spec.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            spec.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

            // Only declare a MIME type we are actually confident about. The
            // system rewrites the saved extension to match the declared type, so
            // a wrong or generic declaration silently renames the user's file
            // (`report.txt` arriving as `report.bin`). When the server is vague
            // and the name is uninformative, letting the platform sniff the body
            // is better than guessing.
            resolveMimeType(request.mimeType, filename)?.let { spec.setMimeType(it) }

            request.userAgent?.takeIf { it.isNotBlank() }
                ?.let { spec.addRequestHeader("User-Agent", it) }

            // The cookie header is what makes authenticated downloads work.
            // Without it the server sees an anonymous request and returns 401,
            // which is the other classic "the download button is broken" report.
            runCatching { CookieManager.getInstance().getCookie(request.url) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { spec.addRequestHeader("Cookie", it) }

            manager.enqueue(spec)
            Log.i("ClearBrowser", "download saved as $filename")
        }.onFailure {
            Log.w("ClearBrowser", "Download failed to start: ${it.message}")
        }
    }

    /**
     * Keep a filename safe for the public Downloads collection.
     *
     * Path separators and control characters in a server-supplied
     * `Content-Disposition` are a real thing, and a download that writes outside
     * Downloads is a bad surprise.
     */
    private fun sanitize(name: String?): String {
        val cleaned = name.orEmpty()
            .substringAfterLast('/')
            .replace(Regex("[\\p{Cntrl}]"), "")
            .trim()
        return cleaned.ifEmpty { "download" }
    }

    /**
     * Settle on one MIME type for both the request and the saved file.
     *
     * These must agree, or the platform rewrites the extension on disk. A server
     * that serves a `.txt` file as `application/octet-stream` -- very common for
     * generated files, and the default for anything a script produces -- would
     * otherwise land as `name.bin`, losing the name the author chose. The
     * filename is the better signal when the server gave us nothing specific.
     */
    private fun resolveMimeType(serverMime: String?, filename: String): String? {
        val fromName = guessMime(filename)
        val generic = serverMime.isNullOrBlank() || serverMime == GENERIC_MIME
        return when {
            // The server said nothing useful, so trust the filename if we can.
            generic -> fromName
            // The server was specific but the name implies a different known
            // type. The name is the better signal: it is what the user sees.
            fromName != null && fromName != GENERIC_MIME -> fromName
            else -> serverMime
        }
    }

    private fun guessMime(filename: String): String? {
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private companion object {
        /**
         * The only schemes `DownloadManager` will accept. Anything else must be
         * refused rather than passed through.
         */
        val DOWNLOADABLE_SCHEMES = setOf("http", "https")

        /** Subdirectory of the cache that capture files are written into. */
        const val CAPTURE_DIR = "camera-captures"

        /**
         * What servers send when they have no better answer. Carries no
         * information, so it must not override a filename that does.
         */
        const val GENERIC_MIME = "application/octet-stream"
    }
}
