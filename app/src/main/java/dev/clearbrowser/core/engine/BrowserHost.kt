package dev.clearbrowser.core.engine

import android.net.Uri

/**
 * The host seam for everything the engine cannot do by itself.
 *
 * File pickers, runtime permission prompts, and downloads all need an Activity
 * and a `ResultLauncher`. The engine must not reach for one, or the GeckoView
 * swap stops being a contained change.
 *
 * Descriptors are deliberately plain data rather than `android.webkit` types,
 * for the same reason: the interface stays engine-neutral.
 */
interface BrowserHost {

    /**
     * Show the system file picker.
     *
     * [onResult] must be called exactly once with the chosen [Uri]s, or null if
     * the user cancelled. WebView blocks the page's `<input type="file">` until
     * its callback is answered, so failing to answer wedges that element
     * permanently.
     *
     * @return false if the request was refused outright, in which case the caller
     *   answers with null itself.
     */
    fun requestFile(request: FileRequest, onResult: (List<Uri>?) -> Unit): Boolean

    /** Ask for runtime permissions, then report the outcome to [onResult]. */
    fun requestPermissions(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit)

    /** Hand a download off to the system. */
    fun startDownload(request: DownloadRequest)
}

/** What the page asked for in `<input type="file">`. */
data class FileRequest(
    val acceptMimeTypes: List<String>,
    val allowsMultiple: Boolean,
    /**
     * True when the page asked for a camera capture rather than a document
     * pick. Worth knowing because the two want different intents.
     */
    val isCapture: Boolean,
)

/** A file the page wants saved. */
data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
    /** Best filename we can infer from the URL or `Content-Disposition`. */
    val suggestedFilename: String?,
)
