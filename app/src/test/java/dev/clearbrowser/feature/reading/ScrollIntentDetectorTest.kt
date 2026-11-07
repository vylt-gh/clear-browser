package dev.clearbrowser.feature.reading

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scroll intent detection rules, verified in isolation.
 *
 * Ensures slow scrolling gestures, reversals, and boundary conditions near
 * the top of a document do not produce spurious direction flips or glitchy
 * transitions between minimized and expanded chrome.
 */
class ScrollIntentDetectorTest {

    @Test
    fun `initial scroll event establishes position and returns NONE`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        val result = detector.onScroll(scrollY = 500, canScrollUp = true, canScrollDown = true)
        assertEquals(ScrollIntentDetector.Direction.NONE, result)
    }

    @Test
    fun `delta below noise floor returns NONE even after direction established`() {
        val detector = ScrollIntentDetector(thresholdPx = 48, minDeltaPx = 2)
        detector.onScroll(500, canScrollUp = true, canScrollDown = true)
        detector.onScroll(560, canScrollUp = true, canScrollDown = true) // DOWN established
        val result = detector.onScroll(561, canScrollUp = true, canScrollDown = true) // delta = 1 < 2
        assertEquals(ScrollIntentDetector.Direction.NONE, result)
    }

    @Test
    fun `slow 1px upward scroll accumulates single-pixel deltas and triggers UP`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        detector.onScroll(500, canScrollUp = true, canScrollDown = true)
        detector.onScroll(560, canScrollUp = true, canScrollDown = true) // DOWN established

        // Scroll UP 1px at a time (e.g. 120Hz display slow drag)
        for (y in 559 downTo 513) {
            assertEquals(ScrollIntentDetector.Direction.NONE, detector.onScroll(y, canScrollUp = true, canScrollDown = true))
        }
        // y = 512 is 48px of upward scroll from 560:
        assertEquals(ScrollIntentDetector.Direction.UP, detector.onScroll(512, canScrollUp = true, canScrollDown = true))
    }

    @Test
    fun `slow upward scroll returns NONE while undecided then triggers UP`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        // First establish position and trigger DOWN
        detector.onScroll(500, canScrollUp = true, canScrollDown = true)
        detector.onScroll(560, canScrollUp = true, canScrollDown = true) // +60px >= 48px -> DOWN

        // Now scroll UP slowly: delta = -10 each frame
        // Must return NONE while undecided (not stale DOWN!)
        assertEquals(ScrollIntentDetector.Direction.NONE, detector.onScroll(550, canScrollUp = true, canScrollDown = true))
        assertEquals(ScrollIntentDetector.Direction.NONE, detector.onScroll(540, canScrollUp = true, canScrollDown = true))
        assertEquals(ScrollIntentDetector.Direction.NONE, detector.onScroll(530, canScrollUp = true, canScrollDown = true))
        assertEquals(ScrollIntentDetector.Direction.NONE, detector.onScroll(520, canScrollUp = true, canScrollDown = true))
        // Accumulated 40px so far. Next 10px crosses 48px threshold:
        assertEquals(ScrollIntentDetector.Direction.UP, detector.onScroll(510, canScrollUp = true, canScrollDown = true))
    }

    @Test
    fun `reversing direction while undecided resets pending accumulator`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        detector.onScroll(500, canScrollUp = true, canScrollDown = true)
        detector.onScroll(560, canScrollUp = true, canScrollDown = true) // DOWN established

        // Scroll UP 30px (undecided)
        detector.onScroll(530, canScrollUp = true, canScrollDown = true)
        // Reverse back DOWN 10px
        detector.onScroll(540, canScrollUp = true, canScrollDown = true)
        // Scroll UP 30px again (should not combine with earlier 30px to reach 60px)
        val result = detector.onScroll(510, canScrollUp = true, canScrollDown = true)
        assertEquals(ScrollIntentDetector.Direction.NONE, result)
    }

    @Test
    fun `at the top of the document chrome is pinned to UP`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        detector.onScroll(500, canScrollUp = true, canScrollDown = true)
        detector.onScroll(560, canScrollUp = true, canScrollDown = true) // DOWN established

        // Reaching the top where canScrollUp is false must immediately return UP
        val result = detector.onScroll(0, canScrollUp = false, canScrollDown = true)
        assertEquals(ScrollIntentDetector.Direction.UP, result)
    }

    @Test
    fun `downward movement near top does not trigger DOWN`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        detector.onScroll(10, canScrollUp = true, canScrollDown = true)
        // Moving down near the top (scrollY <= 48px threshold) should not hide chrome
        val result = detector.onScroll(30, canScrollUp = true, canScrollDown = true)
        assertEquals(ScrollIntentDetector.Direction.NONE, result)
    }

    @Test
    fun `slow downward scroll with micro-jitters accumulates and triggers DOWN`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        detector.onScroll(100, canScrollUp = true, canScrollDown = true)

        // Scroll down slowly with occasional 1px sensor jitter backwards: +3, -1, +3, -1...
        var currentY = 100
        var triggeredDown = false
        for (i in 0 until 40) {
            currentY += 3
            var res = detector.onScroll(currentY, canScrollUp = true, canScrollDown = true)
            if (res == ScrollIntentDetector.Direction.DOWN) {
                triggeredDown = true
                break
            }
            currentY -= 1
            res = detector.onScroll(currentY, canScrollUp = true, canScrollDown = true)
            if (res == ScrollIntentDetector.Direction.DOWN) {
                triggeredDown = true
                break
            }
        }
        assertEquals("Down should be triggered despite 1px backward jitters", true, triggeredDown)
    }

    @Test
    fun `reset clears all state`() {
        val detector = ScrollIntentDetector(thresholdPx = 48)
        detector.onScroll(500, canScrollUp = true, canScrollDown = true)
        detector.onScroll(600, canScrollUp = true, canScrollDown = true) // DOWN established
        detector.reset()

        // After reset, first scroll should be treated as new baseline
        val result = detector.onScroll(600, canScrollUp = true, canScrollDown = true)
        assertEquals(ScrollIntentDetector.Direction.NONE, result)
    }
}
