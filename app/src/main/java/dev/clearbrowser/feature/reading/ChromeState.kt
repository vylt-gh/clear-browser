package dev.clearbrowser.feature.reading

/**
 * What the browser chrome is doing right now.
 *
 * The project's central design claim is that a browser has exactly two
 * personalities and should switch cleanly between them. This enum is that
 * switch: [READING] is near-invisible and the page owns the screen, [SUMMONED]
 * is fully Material and the user asked for it. Everything else is a
 * transitional state.
 */
enum class ChromeMode {
    /** Chrome hidden. The page owns the entire screen. */
    READING,

    /** The floating pill is present. */
    SUMMONED,

    /** The address field is expanded for typing. */
    EXPANDED,

    /** A menu, sheet, or dialog is up. Chrome is pinned regardless of scroll. */
    OVERLAID,
    ;

    /** True when the chrome should be laid out on screen. */
    val isChromeVisible: Boolean get() = this != READING
}

/**
 * Tracks scroll direction to decide when to hide the chrome.
 *
 * The naive version -- hide on any downward scroll -- is unusable: momentum
 * flicks, rubber-band overscroll, and nested scrollers all produce spurious
 * downward movement at rest, and the bar flickers away while you are reading.
 * The guards below are what make it feel calm.
 */
class ScrollIntentDetector(
    /** Movement needed before a direction change is believed. */
    private val thresholdPx: Int = 48,
    /** Movement per event below which the gesture is probably a fling artefact. */
    private val minDeltaPx: Int = 1,
) {
    private var lastY = -1
    private var accumulated = 0
    private var pendingDirection = Direction.NONE
    private var direction = Direction.NONE

    enum class Direction { NONE, DOWN, UP }

    /**
     * Feed a new scroll position.
     *
     * @param canScrollUp whether the page has scrollable content above the current viewport.
     * @param canScrollDown whether the page has scrollable content below the current viewport.
     * @return the accumulated direction, [Direction.NONE] when undecided.
     */
    fun onScroll(scrollY: Int, canScrollUp: Boolean, canScrollDown: Boolean): Direction {
        if (lastY < 0) {
            lastY = scrollY
            return Direction.NONE
        }
        val delta = scrollY - lastY
        lastY = scrollY

        if (delta == 0) return Direction.NONE

        // At the top of the page, the user cannot scroll up further and chrome must stay visible.
        if (!canScrollUp || scrollY <= 0) {
            direction = Direction.UP
            pendingDirection = Direction.NONE
            accumulated = 0
            return Direction.UP
        }

        // Below the noise floor, so not worth crediting to either direction.
        if (kotlin.math.abs(delta) < minDeltaPx) {
            return Direction.NONE
        }

        val observed = if (delta > 0) Direction.DOWN else Direction.UP

        // Hiding while the page is at or near the top would be wrong: the user is
        // not scrolling through content, they are starting a read.
        if (observed == Direction.DOWN && scrollY <= thresholdPx) {
            return Direction.NONE
        }

        if (observed == direction) {
            accumulated = 0
            pendingDirection = Direction.NONE
            return direction
        }

        if (observed != pendingDirection) {
            val absDelta = kotlin.math.abs(delta)
            if (accumulated > 0 && absDelta < accumulated) {
                // Absorb minor counter-movement (micro-jitter) rather than discarding all accumulated progress
                accumulated -= absDelta
                return Direction.NONE
            }
            pendingDirection = observed
            accumulated = 0
        }

        accumulated += kotlin.math.abs(delta)
        if (accumulated >= thresholdPx) {
            direction = observed
            pendingDirection = Direction.NONE
            accumulated = 0
            return direction
        }

        // Undecided during a direction change: do not report the old opposite direction.
        return Direction.NONE
    }

    fun reset() {
        lastY = -1
        accumulated = 0
        pendingDirection = Direction.NONE
        direction = Direction.NONE
    }
}
