package dev.clearbrowser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying pull-to-refresh rubber band damping calculations.
 */
class PullToRefreshDampingTest {

    @Test
    fun zeroOrNegativePullYieldsZeroOffset() {
        assertEquals(0f, WebContainer.calculateDampedOffset(0f, 130f), 0.001f)
        assertEquals(0f, WebContainer.calculateDampedOffset(-50f, 130f), 0.001f)
    }

    @Test
    fun zeroMaxYieldsZeroOffset() {
        assertEquals(0f, WebContainer.calculateDampedOffset(100f, 0f), 0.001f)
    }

    @Test
    fun dampingIsStrictlyMonotonic() {
        val maxPx = 130f
        var prev = 0f
        for (raw in 1..400 step 10) {
            val current = WebContainer.calculateDampedOffset(raw.toFloat(), maxPx)
            assertTrue("Offset at $raw ($current) should be greater than prev ($prev)", current > prev)
            prev = current
        }
    }

    @Test
    fun dampingNeverExceedsMaxPx() {
        val maxPx = 130f
        val hugePull = 10_000f
        val offset = WebContainer.calculateDampedOffset(hugePull, maxPx)
        assertTrue("Offset ($offset) must be strictly less than maxPx ($maxPx)", offset < maxPx)
    }

    @Test
    fun dampingProvidesProgressiveResistance() {
        val maxPx = 130f
        val delta1 = WebContainer.calculateDampedOffset(50f, maxPx) - WebContainer.calculateDampedOffset(0f, maxPx)
        val delta2 = WebContainer.calculateDampedOffset(100f, maxPx) - WebContainer.calculateDampedOffset(50f, maxPx)
        val delta3 = WebContainer.calculateDampedOffset(150f, maxPx) - WebContainer.calculateDampedOffset(100f, maxPx)

        // First 50px of drag yields more displacement than the next 50px
        assertTrue("delta1 ($delta1) > delta2 ($delta2)", delta1 > delta2)
        assertTrue("delta2 ($delta2) > delta3 ($delta3)", delta2 > delta3)
    }
}
