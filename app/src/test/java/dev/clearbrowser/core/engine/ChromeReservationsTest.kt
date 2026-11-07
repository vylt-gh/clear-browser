package dev.clearbrowser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ChromeReservations].
 *
 * The bug these guard against is invisible on screen: a stale rectangle keeps
 * denying touches, so the page quietly stops responding in a region where no
 * chrome is drawn. There is no crash and no red to look for, which is exactly
 * why it needs asserting rather than eyeballing.
 */
class ChromeReservationsTest {

    @Test
    fun `empty set contains nothing`() {
        val r = ChromeReservations()
        assertTrue(r.isEmpty)
        assertFalse(r.contains(10f, 10f))
    }

    @Test
    fun `point inside the reserved rect is chrome`() {
        val r = ChromeReservations()
        r.update("chrome", 0f, 100f, 200f, 200f)
        assertTrue(r.contains(100f, 150f))
    }

    @Test
    fun `point outside the reserved rect is the page`() {
        val r = ChromeReservations()
        r.update("chrome", 0f, 100f, 200f, 200f)
        assertFalse(r.contains(100f, 50f))
        assertFalse(r.contains(250f, 150f))
    }

    @Test
    fun `re-measuring the same owner replaces rather than accumulates`() {
        val r = ChromeReservations()
        r.update("chrome", 0f, 100f, 200f, 200f)
        r.update("chrome", 0f, 300f, 200f, 400f)

        // The old rectangle must no longer deny touches. This is the regression
        // that produced dead zones on the page after the chrome resized.
        assertEquals(1, r.size)
        assertFalse(r.contains(100f, 150f))
        assertTrue(r.contains(100f, 350f))
    }

    @Test
    fun `repeated identical measurements do not grow the set`() {
        val r = ChromeReservations()
        repeat(50) { r.update("chrome", 0f, 100f, 200f, 200f) }
        assertEquals(1, r.size)
    }

    @Test
    fun `several chrome resize cycles leave only the live rect`() {
        val r = ChromeReservations()
        var top = 100f
        repeat(30) {
            top += 4f
            r.update("chrome", 0f, top, 200f, top + 60f)
        }
        assertEquals(1, r.size)
        // Everything above the live rect belongs to the page again.
        assertFalse(r.contains(100f, 110f))
        assertTrue(r.contains(100f, top + 30f))
    }

    @Test
    fun `distinct owners are tracked separately`() {
        val r = ChromeReservations()
        r.update("chrome", 0f, 100f, 200f, 200f)
        r.update("menu", 0f, 500f, 200f, 900f)
        assertEquals(2, r.size)
        assertTrue(r.contains(100f, 150f))
        assertTrue(r.contains(100f, 700f))
        assertFalse(r.contains(100f, 400f))
    }

    @Test
    fun `removing one owner leaves the others`() {
        val r = ChromeReservations()
        r.update("chrome", 0f, 100f, 200f, 200f)
        r.update("menu", 0f, 500f, 200f, 900f)
        r.remove("menu")
        assertEquals(1, r.size)
        assertTrue(r.contains(100f, 150f))
        assertFalse(r.contains(100f, 700f))
    }

    @Test
    fun `degenerate rect is dropped rather than denying a zero-area line`() {
        val r = ChromeReservations()
        r.update("chrome", 100f, 100f, 100f, 200f)
        assertTrue(r.isEmpty)
        assertFalse(r.contains(100f, 150f))

        r.update("chrome", 100f, 100f, 200f, 100f)
        assertTrue(r.isEmpty)
    }

    @Test
    fun `shrinking to a degenerate rect removes the reservation`() {
        val r = ChromeReservations()
        r.update("chrome", 0f, 100f, 200f, 200f)
        // What a collapsed, zero-height chrome reports during its hide animation.
        r.update("chrome", 0f, 100f, 0f, 100f)
        assertTrue(r.isEmpty)
        assertFalse(r.contains(100f, 150f))
    }

    @Test
    fun `clear removes everything`() {
        val r = ChromeReservations()
        r.update("chrome", 0f, 100f, 200f, 200f)
        r.update("menu", 0f, 500f, 200f, 900f)
        r.clear()
        assertTrue(r.isEmpty)
        assertFalse(r.contains(100f, 150f))
    }

    @Test
    fun `owner count is bounded`() {
        val r = ChromeReservations()
        repeat(50) { r.update("owner$it", 0f, it * 10f, 10f, it * 10f + 5f) }
        assertTrue("owner count should stay bounded, was ${r.size}", r.size <= 8)
    }

    @Test
    fun `rect edges are inclusive`() {
        val r = ChromeReservations()
        r.update("chrome", 10f, 10f, 20f, 20f)
        assertTrue(r.contains(10f, 10f))
        assertTrue(r.contains(20f, 20f))
        assertFalse(r.contains(9.9f, 15f))
        assertFalse(r.contains(15f, 20.1f))
    }
}
