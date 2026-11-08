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

    /** Whether pull-to-refresh is enabled by settings and current page context. */
    var pullToRefreshEnabled: Boolean = true

    /** Provider reporting whether the document is currently scrolled at top. */
    var isAtTopProvider: (() -> Boolean)? = null

    /** Callback reporting active pull offset in pixels and the release threshold. */
    var onPullProgress: ((offsetPx: Float, thresholdPx: Float) -> Unit)? = null

    /**
     * Callback when a pull gesture is released.
     * [triggered] is true if pulled past the activation threshold.
     */
    var onPullRelease: ((triggered: Boolean) -> Unit)? = null

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val triggerThresholdPx: Float
        get() = 72f * resources.displayMetrics.density
    private val maxPullPx: Float
        get() = 130f * resources.displayMetrics.density

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var isPullCandidate = false
    private var isPulling = false
    private var currentDampedOffset = 0f

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
            if (isPulling) {
                isPulling = false
                isPullCandidate = false
                currentDampedOffset = 0f
                onPullRelease?.invoke(false)
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                isPulling = false
                currentDampedOffset = 0f
                isPullCandidate = pullToRefreshEnabled && (isAtTopProvider?.invoke() != false)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    val y = event.getY(pointerIndex)
                    val x = event.getX(pointerIndex)
                    val dy = y - downY
                    val dx = x - downX

                    if (isPullCandidate && !isPulling) {
                        if (dy > touchSlop && dy > 1.5f * kotlin.math.abs(dx)) {
                            // User is intentionally dragging downward from top of page
                            isPulling = true
                            // Cancel child GeckoView pan/touch to prevent in-page gestures
                            val cancelEvent = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        } else if (dy < -touchSlop || kotlin.math.abs(dx) > touchSlop) {
                            // Moved up or sideways: disqualify from pull-to-refresh
                            isPullCandidate = false
                        }
                    }

                    if (isPulling) {
                        val rawPull = (dy - touchSlop).coerceAtLeast(0f)
                        currentDampedOffset = calculateDampedOffset(rawPull, maxPullPx)
                        onPullProgress?.invoke(currentDampedOffset, triggerThresholdPx)
                        val wasTouching = isUserTouching
                        isUserTouching = true
                        if (wasTouching != isUserTouching) onUserTouchChanged?.invoke(isUserTouching)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    if (newIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newIndex)
                        downX = event.getX(newIndex)
                        downY = event.getY(newIndex)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (isPulling) {
                    isPulling = false
                    isPullCandidate = false
                    val triggered = currentDampedOffset >= triggerThresholdPx
                    onPullRelease?.invoke(triggered)
                    currentDampedOffset = 0f
                    val wasTouching = isUserTouching
                    isUserTouching = false
                    if (wasTouching != isUserTouching) onUserTouchChanged?.invoke(isUserTouching)
                    return true
                }
                isPullCandidate = false
                currentDampedOffset = 0f
            }
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

    internal companion object {
        const val OWNER_CHROME = "chrome"

        internal fun calculateDampedOffset(rawPull: Float, maxPx: Float): Float {
            if (rawPull <= 0f || maxPx <= 0f) return 0f
            val factor = 1.2f
            val progress = (rawPull * factor) / maxPx
            return maxPx * (1f - 1f / (progress + 1f))
        }
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
