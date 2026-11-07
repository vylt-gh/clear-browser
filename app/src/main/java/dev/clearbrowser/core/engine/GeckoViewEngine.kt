package dev.clearbrowser.core.engine

import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse

/**
 * Mozilla GeckoView implementation of [Engine].
 *
 * Runs Gecko as the web rendering engine with full support for WebExtensions
 * (including uBlock Origin MV2) and strict privacy/tracking protection.
 */
class GeckoViewEngine(
    private val context: Context,
    private val listener: EngineListener,
    private val host: BrowserHost,
    val profile: BrowserProfile = BrowserProfile.DEFAULT,
    private val runtime: GeckoRuntime = ClearGeckoRuntime.get(context),
) : Engine {

    private val geckoView = GeckoView(context)
    private val session: GeckoSession

    private var container: WebContainer? = null
    private var active = true
    private var userTouchActive = false

    private var currentCanGoBack = false
    private var currentCanGoForward = false
    private var currentUrlString: String? = null
    private var currentTitleString: String? = null
    private var currentScrollYPos = 0
    private var lastSessionState: GeckoSession.SessionState? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val topBarSampler = TopBarSampler { color, isLight ->
        listener.onTopBarColorSampled(color, isLight)
    }

    private val contextId: String? = if (profile.isIncognito) {
        "private_${profile.name}"
    } else {
        profile.name
    }

    override var desktopUserAgentEnabled: Boolean = false
        set(value) {
            field = value
            session.settings.userAgentMode = if (value) {
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            }
            if (active) session.reload()
        }

    init {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(profile.isIncognito)
            .apply {
                if (contextId != null) contextId(contextId)
            }
            .useTrackingProtection(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()

        session = GeckoSession(settings)
        installDelegates()
        session.open(runtime)
    }

    private fun installDelegates() {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                currentUrlString = url
                listener.onUrlChanged(url.orEmpty())
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                currentCanGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                currentCanGoForward = canGoForward
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                listener.onNewWindowRequested(uri)
                return null
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val handled = handleUrl(request.uri)
                return if (handled) {
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                } else {
                    GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                currentUrlString = url
                currentScrollYPos = 0
                topBarSampler.reset()
                listener.onUrlChanged(url)
                listener.onLoadStateChanged(LoadState.LOADING)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                listener.onLoadStateChanged(LoadState.COMPLETE)
                mainHandler.postDelayed({
                    topBarSampler.sample(geckoView, currentUrlString, force = true)
                }, 200)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                listener.onProgressChanged(progress)
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                listener.onSecurityStateChanged(securityInfo.isSecure)
            }

            override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) {
                lastSessionState = state
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                currentTitleString = title
                listener.onTitleChanged(title.orEmpty())
            }

            override fun onFirstComposite(session: GeckoSession) {
                listener.onLoadStateChanged(LoadState.COMMITTED)
                listener.onPageCommitted(currentUrlString.orEmpty())
            }

            override fun onFirstContentfulPaint(session: GeckoSession) {
                topBarSampler.sample(geckoView, currentUrlString)
                mainHandler.postDelayed({
                    topBarSampler.sample(geckoView, currentUrlString)
                }, 250)
            }

            override fun onCrash(session: GeckoSession) {
                listener.onRenderProcessGone()
            }

            override fun onKill(session: GeckoSession) {
                listener.onRenderProcessGone()
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val disposition = response.headers["content-disposition"]
                    ?: response.headers["Content-Disposition"]
                val contentType = response.headers["content-type"]
                    ?: response.headers["Content-Type"]
                val contentLength = (response.headers["content-length"]
                    ?: response.headers["Content-Length"])?.toLongOrNull() ?: -1L

                host.startDownload(
                    DownloadRequest(
                        url = response.uri,
                        userAgent = null,
                        contentDisposition = disposition,
                        mimeType = contentType,
                        contentLength = contentLength,
                        suggestedFilename = DownloadFilenameResolver.resolve(
                            response.uri,
                            disposition,
                            contentType,
                        ),
                    ),
                )
            }
        }

        session.compositorScrollDelegate = object : GeckoSession.CompositorScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, update: GeckoSession.ScrollPositionUpdate) {
                val scrollY = (update.scrollY * update.zoom).toInt().coerceAtLeast(0)
                currentScrollYPos = scrollY
                val canUp = scrollY > 0
                val canDown = geckoView.canScrollVertically(1)
                val isUser = update.source == GeckoSession.ScrollPositionUpdate.SOURCE_USER_INTERACTION || userTouchActive
                mainHandler.post {
                    listener.onScrollChanged(
                        scrollY = scrollY,
                        canScrollUp = canUp,
                        canScrollDown = canDown,
                        userTouchActive = isUser,
                    )
                }
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                val list = permissions?.toList() ?: return callback.reject()
                host.requestPermissions(list) { results ->
                    val allGranted = list.all { results[it] == true }
                    if (allGranted) callback.grant() else callback.reject()
                }
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                listener.onJsDialog(
                    JsDialog(
                        message = prompt.message.orEmpty(),
                        defaultValue = null,
                        hasInput = false,
                        withCancel = false,
                        onAccept = { result.complete(prompt.dismiss()) },
                        onDismiss = { result.complete(prompt.dismiss()) },
                    ),
                )
                return result
            }

            override fun onButtonPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                listener.onJsDialog(
                    JsDialog(
                        message = prompt.message.orEmpty(),
                        defaultValue = null,
                        hasInput = false,
                        withCancel = true,
                        onAccept = { result.complete(prompt.confirm(0)) },
                        onDismiss = { result.complete(prompt.dismiss()) },
                    ),
                )
                return result
            }

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                listener.onJsDialog(
                    JsDialog(
                        message = prompt.message.orEmpty(),
                        defaultValue = prompt.defaultValue,
                        hasInput = true,
                        withCancel = true,
                        onAccept = { input -> result.complete(prompt.confirm(input.orEmpty())) },
                        onDismiss = { result.complete(prompt.dismiss()) },
                    ),
                )
                return result
            }

            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                val isCapture = prompt.capture != GeckoSession.PromptDelegate.FilePrompt.Capture.NONE
                val request = FileRequest(
                    acceptMimeTypes = prompt.mimeTypes?.toList() ?: emptyList(),
                    allowsMultiple = multiple,
                    isCapture = isCapture,
                )
                val handled = host.requestFile(request) { uris ->
                    if (uris.isNullOrEmpty()) {
                        result.complete(prompt.dismiss())
                    } else if (multiple) {
                        result.complete(prompt.confirm(context, uris.toTypedArray()))
                    } else {
                        result.complete(prompt.confirm(context, uris.first()))
                    }
                }
                if (!handled) {
                    result.complete(prompt.dismiss())
                }
                return result
            }
        }
    }

    private fun handleUrl(url: String): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        return when (scheme) {
            "http", "https", "about", "data", "file", "javascript", "resource" -> false
            else -> {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                true
            }
        }
    }

    // ------------------------------------------------------------------ Engine

    override fun reserveChromeBounds(bounds: RectF?) {
        container?.reserve(bounds)
    }

    override fun attach(parent: ViewGroup) {
        (parent as? WebContainer)?.let { host ->
            container = host
            host.onUserTouchChanged = { touching ->
                userTouchActive = touching
            }
        }
        if (geckoView.parent === parent) return
        (geckoView.parent as? ViewGroup)?.removeView(geckoView)
        parent.addView(
            geckoView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        geckoView.setSession(session)
        mainHandler.postDelayed({ topBarSampler.sample(geckoView, currentUrlString) }, 200)
    }

    override fun detach() {
        (geckoView.parent as? ViewGroup)?.removeView(geckoView)
        geckoView.releaseSession()
        container?.onUserTouchChanged = null
        container = null
        userTouchActive = false
    }

    override fun loadUrl(url: String) {
        session.loadUri(url)
    }

    override fun loadData(html: String, baseUrl: String?) {
        val loader = GeckoSession.Loader()
            .data(html, "text/html")
        if (!baseUrl.isNullOrBlank()) {
            loader.additionalHeaders(mapOf("Base-URL" to baseUrl))
        }
        session.load(loader)
    }

    override fun reload() = session.reload()

    override fun stop() = session.stop()

    override fun canGoBack(): Boolean = currentCanGoBack

    override fun canGoForward(): Boolean = currentCanGoForward

    override fun goBack() = session.goBack()

    override fun goForward() = session.goForward()

    override fun scrollTo(y: Int) {
        if (y <= 0) {
            session.panZoomController.scrollToTop()
        } else {
            session.panZoomController.scrollTo(
                ScreenLength.zero(),
                ScreenLength.fromPixels(y.toDouble()),
            )
        }
    }

    override fun currentScrollY(): Int = currentScrollYPos

    override fun saveState(): Bundle? {
        val state = lastSessionState ?: return null
        val bundle = Bundle()
        bundle.putParcelable(KEY_SESSION_STATE, state)
        return bundle
    }

    override fun restoreState(state: Bundle): Boolean {
        val sessionState = state.getParcelable<GeckoSession.SessionState>(KEY_SESSION_STATE)
            ?: return false
        session.restoreState(sessionState)
        lastSessionState = sessionState
        return true
    }

    override fun currentUrl(): String? = currentUrlString

    override fun currentTitle(): String? = currentTitleString

    override fun flushCookies() {
        // Handled automatically by Gecko engine runtime
    }

    override fun clearBrowsingData() {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
    }

    override fun clearProfileData() {
        val ctxId = contextId
        if (ctxId != null) {
            runtime.storageController.clearDataForSessionContext(ctxId)
        } else {
            runtime.storageController.clearData(StorageController.ClearFlags.ALL)
        }
    }

    override fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        session.setActive(active)
    }

    override fun destroy() {
        active = false
        (geckoView.parent as? ViewGroup)?.removeView(geckoView)
        geckoView.releaseSession()
        session.close()
    }

    override fun setPageBackgroundColor(color: Int) {
        geckoView.coverUntilFirstPaint(color)
        geckoView.setBackgroundColor(color)
    }

    companion object {
        private const val KEY_SESSION_STATE = "dev.clearbrowser.gecko.session_state"
    }
}
