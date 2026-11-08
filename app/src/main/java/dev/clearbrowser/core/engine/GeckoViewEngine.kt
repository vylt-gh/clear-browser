package dev.clearbrowser.core.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import dev.clearbrowser.R
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
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
    private var navigationDepth = 0
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
        installExtensionActionDelegates()
    }

    private val extensionActions = mutableMapOf<String, WebExtension.Action>()

    private fun createActionDelegate(ext: WebExtension): WebExtension.ActionDelegate {
        return object : WebExtension.ActionDelegate {
            override fun onBrowserAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action,
            ) {
                android.util.Log.i("ClearBrowser", "onBrowserAction: ext=${extension.id}, action=$action")
                extensionActions[extension.id] = action
            }

            override fun onPageAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action,
            ) {
                android.util.Log.i("ClearBrowser", "onPageAction: ext=${extension.id}, action=$action")
                extensionActions[extension.id] = action
            }

            override fun onOpenPopup(
                extension: WebExtension,
                action: WebExtension.Action,
            ): GeckoResult<GeckoSession>? {
                android.util.Log.i("ClearBrowser", "onOpenPopup: ext=${extension.id}")
                return createAndNotifyPopupSession(extension)
            }

            override fun onTogglePopup(
                extension: WebExtension,
                action: WebExtension.Action,
            ): GeckoResult<GeckoSession>? {
                android.util.Log.i("ClearBrowser", "onTogglePopup: ext=${extension.id}")
                return onOpenPopup(extension, action)
            }
        }
    }

    private fun createAndNotifyPopupSession(extension: WebExtension): GeckoResult<GeckoSession> {
        val popupSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(session.settings.usePrivateMode)
            .contextId(session.settings.contextId)
            .build()
        val popupSession = GeckoSession(popupSettings)
        popupSession.open(runtime)
        mainHandler.post {
            listener.onExtensionPopupRequested(
                extension.id,
                extension.metaData.name.orEmpty().ifBlank { "Extension" },
                popupSession,
            )
        }
        return GeckoResult.fromValue(popupSession)
    }

    private fun installExtensionActionDelegates() {
        ClearGeckoRuntime.getRegisteredExtensions().forEach { ext ->
            val delegate = createActionDelegate(ext)
            ext.setActionDelegate(delegate)
            session.webExtensionController.setActionDelegate(ext, delegate)
        }
        ClearGeckoRuntime.listExtensions(runtime).accept(
            { extensions ->
                extensions?.forEach { ext ->
                    val delegate = createActionDelegate(ext)
                    ext.setActionDelegate(delegate)
                    session.webExtensionController.setActionDelegate(ext, delegate)
                }
            },
            { err -> android.util.Log.w("GeckoViewEngine", "Failed to list extensions for delegates", err) },
        )
    }

    private fun installDelegates() {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (currentUrlString != null && !url.isNullOrBlank() && currentUrlString != url) {
                    navigationDepth++
                }
                currentUrlString = url
                listener.onUrlChanged(url.orEmpty())
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                currentCanGoBack = canGoBack
                if (canGoBack) {
                    navigationDepth = navigationDepth.coerceAtLeast(1)
                } else {
                    navigationDepth = 0
                }
                mainHandler.post {
                    listener.onNavigationStateChanged(canGoBack, currentCanGoForward)
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                currentCanGoForward = canGoForward
                mainHandler.post {
                    listener.onNavigationStateChanged(currentCanGoBack, canGoForward)
                }
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val handled = handleUrl(uri)
                if (!handled) {
                    listener.onNewWindowRequested(uri)
                }
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

        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(
                session: GeckoSession,
                historyList: GeckoSession.HistoryDelegate.HistoryList,
            ) {
                val canBack = historyList.currentIndex > 0
                val canForward = historyList.currentIndex < historyList.size - 1
                currentCanGoBack = canBack
                currentCanGoForward = canForward
                navigationDepth = historyList.currentIndex
                mainHandler.post {
                    listener.onNavigationStateChanged(canBack, canForward)
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

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                mainHandler.post {
                    listener.onFullScreenChanged(fullScreen)
                }
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

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                val link = element.linkUri?.ifBlank { null }
                val src = element.srcUri?.ifBlank { null }
                val title = element.title?.ifBlank { null }
                    ?: element.linkText?.ifBlank { null }
                    ?: element.altText?.ifBlank { null }

                if (link != null || src != null) {
                    mainHandler.post {
                        listener.onContextMenu(
                            ContextMenuInfo(
                                linkUrl = link,
                                srcUrl = src,
                                title = title,
                                isImage = element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE,
                                isVideo = element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO,
                            ),
                        )
                    }
                }
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

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                val result = GeckoResult<Int>()
                val hostName = perm.uri.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: perm.uri
                val permTypeStr = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> context.getString(R.string.permission_location)
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> context.getString(R.string.permission_notifications)
                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> context.getString(R.string.permission_storage)
                    else -> "features"
                }

                mainHandler.post {
                    listener.onJsDialog(
                        JsDialog(
                            title = context.getString(R.string.permission_request_title),
                            message = context.getString(R.string.permission_prompt, hostName, permTypeStr),
                            confirmText = context.getString(R.string.permission_allow),
                            dismissText = context.getString(R.string.permission_deny),
                            hasInput = false,
                            withCancel = true,
                            onAccept = {
                                dev.clearbrowser.core.data.SitePermissionStore.forContext(context)
                                    .recordPermission(hostName, permTypeStr)
                                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                            },
                            onDismiss = {
                                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                            },
                        ),
                    )
                }
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                val hostName = runCatching { Uri.parse(uri).host }.getOrNull() ?: uri
                val hasVideo = !video.isNullOrEmpty()
                val hasAudio = !audio.isNullOrEmpty()
                val deviceStr = when {
                    hasVideo && hasAudio -> context.getString(R.string.permission_camera_microphone)
                    hasVideo -> context.getString(R.string.permission_camera)
                    hasAudio -> context.getString(R.string.permission_microphone)
                    else -> "Camera / Microphone"
                }

                mainHandler.post {
                    listener.onJsDialog(
                        JsDialog(
                            title = context.getString(R.string.permission_request_title),
                            message = context.getString(R.string.permission_media_prompt, hostName, deviceStr),
                            confirmText = context.getString(R.string.permission_allow),
                            dismissText = context.getString(R.string.permission_deny),
                            hasInput = false,
                            withCancel = true,
                            onAccept = {
                                dev.clearbrowser.core.data.SitePermissionStore.forContext(context)
                                    .recordPermission(hostName, deviceStr)
                                callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                            },
                            onDismiss = {
                                callback.reject()
                            },
                        ),
                    )
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
                        onAccept = { result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)) },
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
        val action = ExternalUrlHandler.handle(
            url = url,
            canResolve = { intent ->
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
            },
        )
        return when (action) {
            is ExternalUrlAction.PassToBrowser -> false
            is ExternalUrlAction.LaunchIntent -> {
                runCatching { context.startActivity(action.intent) }
                true
            }
            is ExternalUrlAction.LoadFallback -> {
                mainHandler.post {
                    loadUrl(action.fallbackUrl)
                }
                true
            }
            is ExternalUrlAction.HandledNone -> true
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
        if (active && session.isOpen) {
            runtime.webExtensionController.setTabActive(session, true)
        }
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
        if (handleUrl(url)) return
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

    override fun canGoBack(): Boolean = currentCanGoBack || navigationDepth > 0

    override fun canGoForward(): Boolean = currentCanGoForward

    override fun goBack() {
        navigationDepth = (navigationDepth - 1).coerceAtLeast(0)
        session.goBack()
    }

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

    override fun clearBrowsingData(clearCookies: Boolean, clearCache: Boolean) {
        var flags = 0L
        if (clearCookies) {
            flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES
        }
        if (clearCache) {
            flags = flags or StorageController.ClearFlags.ALL_CACHES
        }
        if (flags != 0L) {
            runtime.storageController.clearData(flags)
        }
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
        if (session.isOpen) {
            runtime.webExtensionController.setTabActive(session, active)
        }
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

    override fun exitFullScreen() {
        session.exitFullScreen()
    }

    override fun findInPage(query: String, forward: Boolean) {
        if (query.isBlank()) {
            clearFindInPage()
            return
        }
        session.finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        val flags = if (forward) GeckoSession.FINDER_FIND_FORWARD else GeckoSession.FINDER_FIND_BACKWARDS
        session.finder.find(query, flags).then(
            { result ->
                if (result != null) {
                    mainHandler.post {
                        listener.onFindResult(
                            activeMatch = if (result.found) result.current else 0,
                            totalMatches = if (result.found) result.total else 0,
                        )
                    }
                }
                GeckoResult.fromValue(null)
            },
            { _ ->
                mainHandler.post {
                    listener.onFindResult(activeMatch = 0, totalMatches = 0)
                }
                GeckoResult.fromValue(null)
            },
        )
    }

    override fun clearFindInPage() {
        session.finder.clear()
        mainHandler.post {
            listener.onFindResult(activeMatch = 0, totalMatches = 0)
        }
    }

    override fun triggerExtensionAction(extensionId: String) {
        if (session.isOpen) {
            runtime.webExtensionController.setTabActive(session, true)
        }
        val action = extensionActions[extensionId]
        if (action != null) {
            android.util.Log.i("ClearBrowser", "triggerExtensionAction: clicking action for $extensionId")
            action.click()
        } else {
            android.util.Log.i("ClearBrowser", "triggerExtensionAction: action null for $extensionId, fallback")
            val ext = ClearGeckoRuntime.getExtension(extensionId)
            if (ext != null) {
                val delegate = createActionDelegate(ext)
                ext.setActionDelegate(delegate)
                session.webExtensionController.setActionDelegate(ext, delegate)
            }
            val baseUrl = ext?.metaData?.baseUrl ?: return
            val popupUrl = if (extensionId == ClearGeckoRuntime.UBLOCK_ID) {
                if (baseUrl.endsWith("/")) "${baseUrl}popup-fenix.html" else "$baseUrl/popup-fenix.html"
            } else {
                if (baseUrl.endsWith("/")) "${baseUrl}popup.html" else "$baseUrl/popup.html"
            }
            val popupSettings = GeckoSessionSettings.Builder()
                .usePrivateMode(session.settings.usePrivateMode)
                .contextId(session.settings.contextId)
                .build()
            val popupSession = GeckoSession(popupSettings)
            popupSession.open(runtime)
            popupSession.loadUri(popupUrl)
            mainHandler.post {
                listener.onExtensionPopupRequested(
                    extensionId,
                    ext.metaData.name.orEmpty().ifBlank { "Extension" },
                    popupSession,
                )
            }
        }
    }

    companion object {
        private const val KEY_SESSION_STATE = "dev.clearbrowser.gecko.session_state"
    }
}
