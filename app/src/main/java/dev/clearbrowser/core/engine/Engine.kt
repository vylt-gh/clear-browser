package dev.clearbrowser.core.engine

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Window

/**
 * The seam between the browser's brain and whatever renders the web.
 *
 * P0 ships a single Chromium WebView implementation. GeckoView can be dropped in
 * as a second implementation without `feature:reading` changing, which is why
 * the interface is kept this small and this free of engine-specific types: no
 * `android.webkit.WebView` in any signature, no Gecko classes.
 *
 * Note what is *not* here. A real browser eventually needs multiple windows,
 * extension hosts, and a sync process -- things GeckoView supports and WebView
 * does not. Those belong in [EngineFactory] behind an explicit capability check
 * rather than pretended for here.
 */
interface EngineFactory {
    fun create(context: EngineContext, listener: EngineListener): Engine
}

interface Engine {
    /**
     * Tell the engine which part of the screen the browser's own UI covers, in
     * window coordinates, so the page stops stealing touches from it.
     *
     * @param bounds null clears the reservation, which is what happens when the
     *   chrome animates away and the page should get the whole screen again.
     */
    fun reserveChromeBounds(bounds: android.graphics.RectF?)

    /**
     * Add the engine's view to the container. Called once, before any navigation.
     *
     * `ViewGroup` is a genuine common denominator: WebView and GeckoView both
     * attach this way, so the seam survives an engine swap without an adapter.
     */
    fun attach(parent: ViewGroup)

    /**
     * Take the engine's view out of its container without destroying it.
     *
     * One container hosts one visible WebView, so switching tabs means the
     * outgoing tab has to leave the hierarchy rather than merely be covered. The
     * engine keeps its document, history and scroll position while detached:
     * this is a pause, not a teardown.
     */
    fun detach()

    fun loadUrl(url: String)

    fun loadData(html: String, baseUrl: String? = null)

    fun reload()
    fun stop()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun goBack()
    fun goForward()

    /**
     * Scroll the current page to an absolute offset.
     *
     * Exists for session restore, and is inherently best-effort: the offset is
     * only meaningful once the page is as tall as it is going to get, so callers
     * are expected to re-apply it until it sticks rather than call it once.
     */
    fun scrollTo(y: Int)

    fun currentScrollY(): Int

    /**
     * WebView's own state: history list, scroll, zoom.
     *
     * A [android.os.Bundle] rather than a typed object because it is written
     * straight into `onSaveInstanceState`, which lets the system handle both
     * persistence and unparcelling.
     */
    fun saveState(): android.os.Bundle?

    /**
     * Restore state produced by [saveState]. Must run before the first load, and
     * makes a following [loadUrl] unnecessary: the restored history already
     * includes the current page.
     */
    fun restoreState(state: android.os.Bundle): Boolean

    fun currentUrl(): String?
    fun currentTitle(): String?

    /**
     * Persist cookies to disk.
     *
     * The Activity calls this when backgrounding. Cookies otherwise live in
     * memory, and a browser process is a routine Android-kill target.
     */
    fun flushCookies()

    /** Drop cookies and/or caches for the current browsing context. */
    fun clearBrowsingData(clearCookies: Boolean = true, clearCache: Boolean = true)

    /** Erase everything this profile holds, leaving the view usable. */
    fun clearProfileData()

    /** Pause timers, animations and media. Called when the tab is not visible. */
    fun setActive(active: Boolean)

    /** Release native resources. The engine is unusable afterwards. */
    fun destroy()

    var desktopUserAgentEnabled: Boolean

    /**
     * Paint the engine's own background.
     *
     * Only here so the engine can pick a value that cannot be a black flash. The
     * WebView's default background is opaque white (or black in a dark-themed
     * context) and it is visible for every frame between "navigation started"
     * and "first pixels painted", which is the most visible flash in a browser.
     *
     * A neutral, mode-appropriate surface is the right answer. It used to be
     * the sampled page colour, which meant the toolbar briefly showed a colour
     * the page had not committed to yet.
     */
    fun setPageBackgroundColor(color: Int)

    /** Exit HTML5 fullscreen element/video playback. */
    fun exitFullScreen() = Unit

    /** Search for text within the current page. */
    fun findInPage(query: String, forward: Boolean = true) = Unit

    /** Clear active page find highlights. */
    fun clearFindInPage() = Unit
}

/**
 * Events the browser chrome cares about. Deliberately coarse: the chrome is a
 * thin observer, and a smaller surface is a smaller port when the engine changes.
 */
interface EngineListener {
    fun onLoadStateChanged(state: LoadState) = Unit
    fun onProgressChanged(progress: Int) = Unit
    fun onUrlChanged(url: String) = Unit
    fun onTitleChanged(title: String) = Unit
    fun onSecurityStateChanged(secure: Boolean) = Unit
    /**
     * Scroll position in the view's own pixel space.
     *
     * `canScrollUp`/`canScrollDown` are passed rather than a content height
     * because they are the only signals guaranteed to share a unit with
     * `scrollY`. `WebView.getContentHeight()` reports CSS pixels while scroll
     * offsets are physical pixels, and mixing the two is how "is there more
     * below" ends up subtly wrong on high-density screens.
     */
    fun onScrollChanged(
        scrollY: Int,
        canScrollUp: Boolean,
        canScrollDown: Boolean,
        userTouchActive: Boolean,
    ) = Unit


    /**
     * The page has painted its first frame. The right moment for a pixel sample:
     * before it, the view still shows the background colour and every sample
     * would just tell us what we already know.
     */
    fun onPageCommitted(url: String) = Unit

    fun onRenderProcessGone() = Unit

    /** A `target=_blank` or `window.open` navigation the engine handed upward. */
    fun onNewWindowRequested(url: String) = Unit

    /** A JS `alert`/`confirm`/`prompt` the host must present. */
    fun onJsDialog(dialog: JsDialog) = Unit

    /**
     * Dominant or average top bar color sampled from the webpage, and whether the color is light.
     */
    fun onTopBarColorSampled(color: Int, isLight: Boolean) = Unit

    /** HTML5 video or element entered or exited fullscreen mode. */
    fun onFullScreenChanged(fullScreen: Boolean) = Unit

    /** Active find in page result. */
    fun onFindResult(activeMatch: Int, totalMatches: Int) = Unit

    /** Context menu requested (e.g. long-press on link, image, or media). */
    fun onContextMenu(info: ContextMenuInfo) = Unit
}

/** Information about a context element tapped or long-pressed. */
data class ContextMenuInfo(
    val linkUrl: String? = null,
    val srcUrl: String? = null,
    val title: String? = null,
    val isImage: Boolean = false,
    val isVideo: Boolean = false,
)

/**
 * A JS dialog, in engine-neutral terms.
 *
 * WebView suppresses these unless the app answers them, and an unanswered
 * `alert()` leaves the promise on the page pending forever -- which looks
 * exactly like a frozen tab. So this must be presented and answered.
 */
data class JsDialog(
    val message: String,
    val defaultValue: String? = null,
    /** True for `prompt`, false for `alert` and `confirm`. */
    val hasInput: Boolean = false,
    /** True for `confirm` and `prompt`, which can be cancelled. */
    val withCancel: Boolean = false,
    val title: String? = null,
    val confirmText: String = "OK",
    val dismissText: String = "Cancel",
    val onAccept: (String?) -> Unit,
    val onDismiss: () -> Unit,
)

enum class LoadState { IDLE, LOADING, COMMITTED, COMPLETE }

/** Handle back to the host Activity, needed for file chooser and permissions. */
interface EngineContext {
    val activity: Activity
}
