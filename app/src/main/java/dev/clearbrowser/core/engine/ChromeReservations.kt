package dev.clearbrowser.core.engine

/**
 * The set of screen areas the browser's own UI is covering.
 *
 * This is deliberately plain Kotlin with no Android types. The rule it encodes is
 * easy to get subtly wrong and impossible to test through a `View`, and getting
 * it wrong produces dead zones on the page that only show up as "this bit of the
 * page doesn't respond to taps".
 *
 * ## Why this replaces rather than accumulates
 *
 * The previous behaviour appended every distinct rect it was given and only
 * cleared when handed null. That treats a *current* measurement as if it were
 * another entry in a history, which is the wrong model: the chrome occupies one
 * region at a time, and every earlier measurement of that region is by
 * definition stale.
 *
 * Keeping stale rects is not a harmless leak. Each one still denies touches, so
 * the union of all of them grows every time the chrome resizes -- expanding the
 * omnibox, the menu sheet opening, the navigation inset changing. The page
 * silently loses the ability to be tapped in the gaps the chrome used to cover,
 * with nothing on screen to explain it. A stale *denial* is worse than a stale
 * allowance: at worst a tap near the toolbar scrolls the page instead of
 * pressing a button, whereas here the tap is swallowed by nothing at all.
 */
class ChromeReservations {

    /**
     * Rects in window coordinates, as left/top/right/bottom.
     *
     * A list rather than a single rect because the chrome genuinely is more than
     * one box: the pill and, when it is showing, the menu sheet. Keyed by [owner]
     * so a re-measurement replaces its predecessor instead of adding to it.
     */
    private val entries = LinkedHashMap<String, Box>()

    /** How many distinct owners we will track, as a runaway guard. */
    private val capacity = 8

    /**
     * Record that [owner] covers [left, top, right, bottom] in window coordinates.
     *
     * Re-measuring the same owner replaces the previous rectangle, which is the
     * whole point: only the latest geometry is live.
     */
    fun update(owner: String, left: Float, top: Float, right: Float, bottom: Float) {
        // A degenerate rect covers nothing and would otherwise deny a touch on a
        // zero-area line, which is exactly the sort of invisible bug this class
        // exists to prevent.
        if (right <= left || bottom <= top) {
            entries.remove(owner)
            return
        }
        entries[owner] = Box(left, top, right, bottom)
        while (entries.size > capacity) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    /** Forget one owner, e.g. because its view left the composition. */
    fun remove(owner: String) {
        entries.remove(owner)
    }

    fun clear() = entries.clear()

    val isEmpty: Boolean get() = entries.isEmpty()

    val size: Int get() = entries.size

    /** True when the point belongs to the browser's UI rather than the page. */
    fun contains(x: Float, y: Float): Boolean = entries.values.any { it.contains(x, y) }

    /** A snapshot, for asserting on in tests without exposing the backing map. */
    fun snapshot(): List<Box> = entries.values.toList()

    /**
     * Axis-aligned rectangle. A local type so this class stays free of
     * `android.graphics.RectF`, which cannot be constructed off-device.
     */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
    }
}
