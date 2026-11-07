package dev.clearbrowser.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * The ViewGroup that hosts the engine's view, and the arbiter of who gets a touch.
 *
 * This class carries two responsibilities that both exist for the same reason:
 * the browser's UI floats *over* a full-screen WebView, and that is a
 * structural conflict, not a styling detail.
 *
 * ## 1. Touches must reach the chrome
 *
 * An `AndroidView` embeds a real Android `View` inside the Compose tree. That
 * view fills the screen, and Android gives a child View priority over the
 * parent's own touch handling, so the WebView swallowed *every* tap: the address
 * bar, the menu, everything. Measured on device, Compose's root pointer input
 * received no events at all.
 *
 * The fix is to make the container decline touches that belong to the chrome.
 * Returning `false` from [dispatchTouchEvent] makes Android fall through to this
 * ViewGroup's parent, which is the `AndroidComposeView` whose `onTouchEvent` runs
 * Compose's hit testing. So declining is not "drop the event" -- it is "let
 * someone else who can actually handle it have it". The chrome registers its
 * bounds via [reserve]; anything outside them goes to the page, which is the
 * correct behaviour for a browser.
 *
 * ## 2. The chrome must ignore scrolls the user did not cause
 *
 * [isUserTouching] reports whether a finger is down on the content. The chrome's
 * auto-hide needs it, because plenty of pages scroll themselves -- carousels,
 * parallax heroes, lazy galleries. Driving the chrome from raw scroll position
 * made it flicker in and out on any of those, which reads as a bug in the
 * browser rather than a behaviour in the page.
 *
 * This replaced `WebViewClient.onPageScrolled`, which used to say whether a
 * gesture was user-initiated and was removed in API 37.
 */
class WebContainer(context: Context) : FrameLayout(context) {

    /** True between ACTION_DOWN and ACTION_UP on the page itself. */
    var isUserTouching: Boolean = false
        private set

    var onUserTouchChanged: ((Boolean) -> Unit)? = null

    /**
     * Chrome bounds in *window* coordinates. The page is denied touches inside
     * these rects.
     *
     * Window coordinates rather than local ones because that is what the Compose
     * side can supply directly (`LayoutCoordinates.boundsInWindow()`), and
     * converting in one place beats converting in two.
     *
     * The replace-not-accumulate rule lives in [ChromeReservations], which is
     * plain Kotlin and unit tested; this class only adapts coordinates.
     */
    private val reservations = ChromeReservations()

    /** Offset of this container within the window, for the coordinate conversion. */
    private var locationInWindow = intArrayOf(0, 0)

    /**
     * Declare the area the browser's own UI occupies, identified by [owner].
     *
     * Passing null clears everything, which is what happens when the chrome
     * animates away. A repeated call for the same owner *replaces* its previous
     * rectangle rather than adding another: only the latest geometry is live.
     */
    @JvmOverloads
    fun reserve(boundsInWindow: RectF?, owner: String = OWNER_CHROME) {
        if (boundsInWindow == null) {
            reservations.clear()
            return
        }
        reservations.update(
            owner = owner,
            left = boundsInWindow.left,
            top = boundsInWindow.top,
            right = boundsInWindow.right,
            bottom = boundsInWindow.bottom,
        )
    }

    fun clearReservations() = reservations.clear()

    /**
     * True when nothing is reserved, i.e. every touch reaches the page.
     *
     * Used by the WebView's own touch handling to skip the window-coordinate
     * conversion entirely in the common case, and asserted on in tests.
     */
    internal fun hasReservations(): Boolean = !reservations.isEmpty

    internal fun reservationCount(): Int = reservations.size

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (belongsToChrome(event)) {
            // Decline, so the parent Compose view hit-tests it instead. The
            // page underneath deliberately does not see these touches either:
            // a tap on the toolbar should not also press the content behind it.
            return false
        }

        val wasTouching = isUserTouching
        isUserTouching = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
            else -> isUserTouching
        }
        if (wasTouching != isUserTouching) onUserTouchChanged?.invoke(isUserTouching)

        return super.dispatchTouchEvent(event)
    }

    private fun belongsToChrome(event: MotionEvent): Boolean {
        if (reservations.isEmpty) return false
        val x = locationInWindow[0] + event.x
        val y = locationInWindow[1] + event.y
        return reservations.contains(x, y)
    }

    private companion object {
        const val OWNER_CHROME = "chrome"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        getLocationInWindow(locationInWindow)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        getLocationInWindow(locationInWindow)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
