package dev.clearbrowser.core.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.net.Uri
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLDecoder
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Chromium WebView as the rendering engine.
 *
 * Everything the browser UI needs is funnelled through [EngineListener] so that
 * `feature:reading` never touches an `android.webkit` type. The two
 * adaptive-chrome specifics -- the document-start theme script and the message
 * bridge it reports through -- live here because both are engine-API surface
 * and would not survive a swap.
 */
class WebViewEngine(
    private val context: Context,
    private val listener: EngineListener,
    private val host: BrowserHost,
    val profile: BrowserProfile = BrowserProfile.DEFAULT,
) : Engine {

    private val webView = WebView(context)
    private val cookies = CookiePolicy(webView)
    private val main = Handler(Looper.getMainLooper())
    private var userTouchActive = false

    private var active = true
    private var lastReportedScrollY = -1
    private var lastReportedCanScrollDown = false
    private var lastReportedUserTouch = false
    private var lastReportedFrameNanos = 0L

    override var desktopUserAgentEnabled: Boolean = false
        set(value) {
            field = value
            val settings = webView.settings
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString = if (value) {
                settings.userAgentString
                    .replace("Android", "X11; Linux x86_64")
                    .replace("Mobile Safari", "Safari")
            } else {
                DEFAULT_MOBILE_UA
            }
        }

    init {
        // Before anything is loaded: the profile is fixed at construction, which
        // is why changing it means building a new engine.
        BrowserProfile.applyTo(webView, profile)
        cookies.apply()
        configureSettings()
        // The stretch-overscroll glow paints its own background, and on a dark
        // page that is the second source of a black flash behind the toolbar.
        // `never` is right for a browser: the page supplies its own overscroll.
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        installDownloadListener()
        installClients()
        startScrollPolling()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings() = with(webView.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        // The browser contract: we handle our own zoom, and only widen the
        // viewport for sites that misbehave.
        useWideViewPort = false
        loadWithOverviewMode = true
        builtInZoomControls = false
        displayZoomControls = false
        setSupportZoom(false)
        mediaPlaybackRequiresUserGesture = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        userAgentString = DEFAULT_MOBILE_UA

        // Do NOT algorithmically invert light pages when the phone is in dark
        // mode. Chrome and Safari both leave a light site light, and a browser
        // that inverts sites behind the user's back is one nobody trusts. Our
        // chrome adapts separately, from the sampled page colour.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }
    }

    /**
     * Wire downloads.
     *
     * Absent this, tapping a PDF or an archive does nothing at all, with no
     * error anywhere -- the page simply never reacts.
     */
    @SuppressLint("SetJavaScriptEnabled")
    /**
     * Work out the name to save a download under.
     *
     * ## Why this is not just `URLUtil.guessFileName`
     *
     * `guessFileName(url, contentDisposition, mimeType)` trusts [mimeType] when
     * the disposition does not pin a name, and a server sending
     * `Content-Type: application/octet-stream` for a `.txt` file is extremely
     * common -- it is the default for anything a script generates. The call then
     * returns `report.bin`, and no amount of downstream MIME fixing can recover
     * the name, because the name is already wrong by the time we get it.
     *
     * So the disposition is parsed first, and only if it yields nothing does the
     * platform helper get to decide. `URLUtil.guessFileName` is still the
     * fallback, because it knows the RFC 6266 details (encoded `filename*`,
     * relative paths) better than a hand-rolled parser would.
     */
    private fun installDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            host.startDownload(
                DownloadRequest(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    contentLength = contentLength,
                    suggestedFilename = DownloadFilenameResolver.resolve(url, contentDisposition, mimeType),
                ),
            )
        }
    }

    /**
     * The Android permission a WebView resource maps to, or null if this app does
     * not support it.
     *
     * Returning null rather than a default matters: an unrecognised resource must
     * be denied outright, not quietly checked against an unrelated permission.
     *
     * ## Why the resource names are the `android.webkit.resource.*` family
     *
     * WebView does not send Android permission strings here. It sends its own
     * vocabulary -- `android.webkit.resource.AUDIO_CAPTURE` -- which is what
     * made this fail silently for so long: a mapping written against
     * `android.permission.RECORD_AUDIO` never matches, every microphone request
     * lands in the "unrecognised" branch, and the page is denied with nothing on
     * screen to say a prompt was ever expected. The platform constant
     * `PermissionRequest.RESOURCE_AUDIO_CAPTURE` is used directly where it
     * exists, so a WebView upgrade cannot leave a stale string behind.
     */
    private fun mapWebPermission(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
            android.Manifest.permission.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
            android.Manifest.permission.CAMERA
        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID ->
            null
        else -> when {
            // Older and OEM builds have been seen sending the plain Android
            // permission string, so keep accepting that spelling too.
            resource.startsWith(RESOURCE_RECORD_AUDIO) ->
                android.Manifest.permission.RECORD_AUDIO
            resource.startsWith(RESOURCE_CAMERA) ->
                android.Manifest.permission.CAMERA
            resource == RESOURCE_GEOLOCATION || resource.startsWith(PERMISSION_FINE_LOCATION) ->
                android.Manifest.permission.ACCESS_FINE_LOCATION
            else -> null
        }
    }

    private fun installClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                lastReportedScrollY = -1
                listener.onUrlChanged(url)
                listener.onLoadStateChanged(LoadState.LOADING)
            }

            /**
             * The first moment real pixels exist. This is where the top-band
             * sample is worth taking, and where the chrome colour should already
             * have been set by the document-start script or the LRU cache.
             */
            override fun onPageCommitVisible(view: WebView, url: String) {
                listener.onLoadStateChanged(LoadState.COMMITTED)
                listener.onPageCommitted(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                listener.onTitleChanged(view.title.orEmpty())
                listener.onLoadStateChanged(LoadState.COMPLETE)
                listener.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                listener.onUrlChanged(url)
                listener.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = handleUrl(request.url.toString())

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame) {
                    // Sub-resource 404s are the page's business, not ours.
                    listener.onLoadStateChanged(LoadState.COMPLETE)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    listener.onLoadStateChanged(LoadState.COMPLETE)
                }
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError,
            ) {
                // Never proceed. A browser that silently accepts a bad
                // certificate is not a browser.
                handler.cancel()
                listener.onSecurityStateChanged(false)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Returning false here takes the app down with the renderer.
                // Report it and let the host decide how to recover.
                listener.onRenderProcessGone()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                listener.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                listener.onTitleChanged(title.orEmpty())
            }

            /**
             * `target="_blank"` and `window.open`.
             *
             * WebView keeps exactly one pending popup at a time and hands us a
             * throwaway child WebView to adopt. Returning false without adopting
             * it means the link silently does nothing, which is how a meaningful
             * number of links across the web appear broken in a naive WebView
             * browser. The placeholder child is adopted, its URL is read from the
             * transport, and the real navigation happens in the current tab.
             */
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

                // Must be a different instance from the opener, and its client
                // must be installed *before* the transport is fulfilled, or the
                // child's very first navigation can land before anyone is
                // listening and the URL is lost.
                val child = WebView(view.context)
                child.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                        if (url != "about:blank") {
                            listener.onNewWindowRequested(url)
                            v.stopLoading()
                            // The real page is loading in the main view; this
                            // throwaway exists only to carry the URL across.
                            v.destroy()
                        }
                    }

                    /**
                     * This child is a URL courier, not a browsing surface, so a
                     * dead renderer here has nothing to report and nothing to
                     * save. Destroying it is the whole recovery.
                     *
                     * Returning false would let the process be taken down with
                     * the renderer, and dropping the callback without
                     * destroying leaks a WebView for every popup the user opens.
                     */
                    override fun onRenderProcessGone(
                        v: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        v.destroy()
                        return true
                    }
                }
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean = host.requestFile(
                FileRequest(
                    acceptMimeTypes = fileChooserParams.acceptTypes.filter { it.isNotBlank() },
                    allowsMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                    isCapture = fileChooserParams.isCaptureEnabled,
                ),
            ) { uris ->
                // Answered on every path, including cancel. Leaving this
                // unclaimed blocks that input element for the page's lifetime.
                filePathCallback.onReceiveValue(uris?.toTypedArray())
            }

            // Without these, any page calling alert() or confirm() appears to
            // hang forever, because WebView suppresses the dialog by default and
            // the promise never settles.
            override fun onJsAlert(
                view: WebView,
                url: String?,
                message: String?,
                result: JsResult,
            ): Boolean {
                listener.onJsDialog(
                    JsDialog(
                        message = message.orEmpty(),
                        defaultValue = null,
                        hasInput = false,
                        withCancel = false,
                        onAccept = { runCatching { result.confirm() } },
                        onDismiss = { runCatching { result.cancel() } },
                    ),
                )
                return true
            }

            override fun onJsConfirm(
                view: WebView,
                url: String?,
                message: String?,
                result: JsResult,
            ): Boolean {
                listener.onJsDialog(
                    JsDialog(
                        message = message.orEmpty(),
                        defaultValue = null,
                        hasInput = false,
                        withCancel = true,
                        onAccept = { runCatching { result.confirm() } },
                        onDismiss = { runCatching { result.cancel() } },
                    ),
                )
                return true
            }

            override fun onJsPrompt(
                view: WebView,
                url: String?,
                message: String?,
                defaultPrompt: String?,
                result: JsPromptResult,
            ): Boolean {
                listener.onJsDialog(
                    JsDialog(
                        message = message.orEmpty(),
                        defaultValue = defaultPrompt,
                        hasInput = true,
                        withCancel = true,
                        onAccept = { input -> runCatching { result.confirm(input.orEmpty()) } },
                        onDismiss = { runCatching { result.cancel() } },
                    ),
                )
                return true
            }

            // Note the missing WebView parameter: it was removed in API 37.
            override fun onPermissionRequest(request: PermissionRequest) {
                // Map once, and keep the pairing. The previous version mapped
                // the resources to permissions twice, and the two `when` blocks
                // had drifted apart: the second one had no GEOLOCATION case and
                // fell through to `else -> ACCESS_FINE_LOCATION`. A request for
                // a resource this app does not handle at all was therefore
                // checked against the location permission, and if the user had
                // granted location for some other reason the page got a grant it
                // never asked the user about.
                val recognised = request.resources.mapNotNull { resource ->
                    mapWebPermission(resource)?.let { resource to it }
                }
                if (recognised.isEmpty()) {
                    Log.w(
                        TAG,
                        "denying unrecognised permission request: ${request.resources.toList()}",
                    )
                    request.deny()
                    return
                }

                val permissions = recognised.map { it.second }.distinct()
                host.requestPermissions(permissions) { results ->
                    // Grant only the resources the user actually approved.
                    // Granting the original list would hand over anything the
                    // page asked for, including resources this app deliberately
                    // does not support, so an unrecognised capability could slip
                    // through alongside a recognised one.
                    val approved = recognised.filter { (_, permission) ->
                        results[permission] == true
                    }.map { it.first }

                    // The framework drops the request once granted or denied, so
                    // a later page must prompt again. Denying after granting would
                    // revoke the grant we just made.
                    if (approved.isEmpty()) {
                        request.deny()
                    } else {
                        request.grant(approved.toTypedArray())
                    }
                }
            }
        }
    }


    private fun startScrollPolling() {
        val tick = object : Runnable {
            override fun run() {
                if (!active) return
                val y = webView.scrollY
                val canDown = webView.canScrollVertically(1)
                val changed = y != lastReportedScrollY ||
                    canDown != lastReportedCanScrollDown ||
                    userTouchActive != lastReportedUserTouch
                if (changed) {
                    lastReportedScrollY = y
                    lastReportedCanScrollDown = canDown
                    lastReportedUserTouch = userTouchActive
                    listener.onScrollChanged(
                        scrollY = y,
                        canScrollUp = y > 0,
                        canScrollDown = canDown,
                        userTouchActive = userTouchActive,
                    )
                }
                scheduleNextTick(this)
            }
        }
        main.post(tick)
    }

    /**
     * Post the next poll on a frame boundary.
     *
     * Choreographer rather than a fixed 16ms timer because this loop exists to
     * observe frames: once per frame means the answer is never stale, and a
     * timer means the work sometimes lands between frames, competing with the
     * draw the user is waiting for.
     *
     * Backs off to a plain timer when frames are running long. If the display
     * cannot keep up, polling on its cadence would pile more work onto an
     * already-late frame, which is how a stutter turns into a freeze.
     */
    private fun scheduleNextTick(tick: Runnable) {
        if (!active) return
        val choreographer = runCatching { Choreographer.getInstance() }.getOrNull()
        if (choreographer == null) {
            main.postDelayed(tick, FRAME_INTERVAL_MS)
            return
        }
        choreographer.postFrameCallback(
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!active) return
                    val elapsedMs = (frameTimeNanos - lastReportedFrameNanos) / 1_000_000L
                    lastReportedFrameNanos = frameTimeNanos
                    if (elapsedMs > SLOW_FRAME_MS) {
                        main.postDelayed(tick, FRAME_INTERVAL_MS)
                    } else {
                        tick.run()
                    }
                }
            },
        )
    }

    private fun handleUrl(url: String): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        return when (scheme) {
            // Let the engine load anything it understands itself.
            "http", "https", "about", "data", "file", "javascript" -> false
            // Hand everything else to whatever app claims it.
            else -> {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                true
            }
        }
    }

    // ------------------------------------------------------------------ Engine

    private var container: WebContainer? = null

    override fun reserveChromeBounds(bounds: android.graphics.RectF?) {
        container?.reserve(bounds)
    }

    override fun attach(parent: ViewGroup) {
        // Idempotent: the Compose `update` block runs on every recomposition and
        // addView would otherwise stack duplicate children.
        // The container is created by the UI and handed over; the engine only
        // fills it. Creating it here would be circular, since the Compose
        // factory that hosts it runs before attach.
        (parent as? WebContainer)?.let { host ->
            container = host
            host.onUserTouchChanged = { touching ->
                userTouchActive = touching
                // The moment a finger lifts, stop crediting scroll movement to
                // the user, so momentum scrolling after release cannot drive the
                // chrome.
                if (!touching) lastReportedScrollY = -1
            }
        }
        if (webView.parent === parent) return
        (webView.parent as? ViewGroup)?.removeView(webView)
        parent.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    /**
     * Leave the container, keeping the document.
     *
     * The touch callback is cleared as well as the view removed, because the
     * container outlives the engine: a detached WebView still holding the
     * "finger is down" callback would keep reporting touches on a page that is
     * no longer on screen, which would drive the chrome's auto-hide from a tab
     * the user cannot see.
     */
    override fun detach() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        container?.onUserTouchChanged = null
        container = null
        active = false
        main.removeCallbacksAndMessages(null)
        userTouchActive = false
    }

    override fun loadData(html: String, baseUrl: String?) {
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
    }

    override fun reload() = webView.reload()

    override fun stop() = webView.stopLoading()

    override fun canGoBack() = webView.canGoBack()

    override fun canGoForward() = webView.canGoForward()

    override fun goBack() {
        webView.goBack()
        listener.onNavigationStateChanged(webView.canGoBack(), webView.canGoForward())
    }

    override fun goForward() {
        webView.goForward()
        listener.onNavigationStateChanged(webView.canGoBack(), webView.canGoForward())
    }

    override fun currentUrl(): String? = webView.url

    override fun currentTitle(): String? = webView.title

    override fun scrollTo(y: Int) {
        if (y <= 0) return
        webView.scrollTo(0, y)
    }

    override fun currentScrollY(): Int = webView.scrollY

    /**
     * Note the API shape: as of API 37 `WebView.saveState` writes *into* a
     * caller-supplied Bundle and returns a `WebBackForwardList` rather than
     * returning the Bundle. The older `saveState(): Bundle` form is gone.
     */
    override fun saveState(): android.os.Bundle? = runCatching {
        val out = android.os.Bundle()
        // Null means there was no history worth recording, which is normal for
        // a blank tab and must not be mistaken for success.
        if (webView.saveState(out) != null) out else null
    }.getOrNull()

    override fun restoreState(state: android.os.Bundle): Boolean = runCatching {
        // Must run before the first load and on an unattached WebView, which is
        // why the caller does it during attach.
        webView.restoreState(state) != null
    }.getOrDefault(false)

    override fun flushCookies() = cookies.flush()

    override fun clearBrowsingData(clearCookies: Boolean, clearCache: Boolean) {
        if (clearCookies) {
            cookies.clearAll()
            webView.clearFormData()
        }
        if (clearCache) {
            webView.clearCache(true)
        }
    }

    /**
     * Discard everything this profile holds, without touching the view.
     *
     * For incognito this is belt-and-braces: the data lives in a separate
     * profile that nothing else references, so it is already unreachable once
     * the window closes. Scrubbing as well means a stale profile cannot resurface
     * a session if the name is ever reused.
     */
    override fun clearProfileData() {
        cookies.clearAll()
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        runCatching { webView.clearSslPreferences() }
    }

    override fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (active) {
            webView.onResume()
            webView.resumeTimers()
        } else {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    override fun destroy() {
        // Stops the poll before anything else. `active` is the flag the poll
        // checks, and it is also what stops a Choreographer callback already in
        // flight from re-arming itself, so it has to go first.
        active = false
        // Last chance to persist cookies before this process goes away.
        cookies.flush()
        main.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }


    /** The Activity window, which is the only PixelCopy source for a WebView. */

    override fun setPageBackgroundColor(color: Int) {
        webView.setBackgroundColor(color)
    }

    companion object {
        /**
         * WebView's own default UA varies by OEM and by WebView version, which
         * makes sites behave inconsistently across devices. Pinning it is a
         * deliberate consistency choice.
         */
        private const val DEFAULT_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 16; Pixel) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36"

        private const val FRAME_INTERVAL_MS = 16L

        /**
         * A frame longer than this means the device is already behind, so the
         * scroll poll drops to a timer rather than adding to a late frame.
         */
        private const val SLOW_FRAME_MS = 24L

        /** RFC 5987 extended parameter, which is the authoritative filename form. */
        private const val EXTENDED = "filename*"

        private const val PLAIN = "filename"

        private const val TAG = "ClearBrowser"

        private const val RESOURCE_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val RESOURCE_CAMERA = "android.permission.CAMERA"
        private const val RESOURCE_GEOLOCATION = "android.webkit.resource.GEOLOCATION"
        private const val PERMISSION_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    }
}
