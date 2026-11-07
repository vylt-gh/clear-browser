package dev.clearbrowser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TopBarSampler]'s color analysis algorithm:
 * - Tests dominant majority color extraction (>= 50% majority).
 * - Tests average color fallback when topbar is mixed/diverse (< 50% single color).
 * - Tests light vs dark luminance contrast calculation.
 */
class TopBarSamplerTest {

    private val sampler = TopBarSampler { _, _ -> }

    @Test
    fun `empty or transparent pixels return null`() {
        val empty = emptyList<Int>()
        assertNull(sampler.analyzePixels(empty))

        val transparent = listOf(
            0x00FFFFFF, // Alpha = 0
            0x40FF6600, // Alpha = 64 (< 128)
        )
        assertNull(sampler.analyzePixels(transparent))
    }

    @Test
    fun `overwhelming majority color selects dominant color`() {
        // Hacker News orange: #ff6600 -> 0xFFFF6600
        val hnOrange = 0xFFFF6600.toInt()
        val white = 0xFFFFFFFF.toInt()

        // 80% orange, 20% white
        val pixels = ArrayList<Int>()
        repeat(80) { pixels.add(hnOrange) }
        repeat(20) { pixels.add(white) }

        val result = sampler.analyzePixels(pixels)
        assertNotNull(result)

        // Result should be the dominant orange color
        val r = (result!!.color ushr 16) and 0xFF
        val g = (result.color ushr 8) and 0xFF
        val b = result.color and 0xFF

        assertEquals(0xFF, r)
        assertEquals(0x66, g)
        assertEquals(0x00, b)
    }

    @Test
    fun `mixed colors with no single majority calculates average color`() {
        // 40% pure Red (0xFFFF0000), 30% pure Green (0xFF00FF00), 30% pure Blue (0xFF0000FF)
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()

        val pixels = ArrayList<Int>()
        repeat(40) { pixels.add(red) }
        repeat(30) { pixels.add(green) }
        repeat(30) { pixels.add(blue) }

        val result = sampler.analyzePixels(pixels)
        assertNotNull(result)

        // Expected weighted averages:
        // R: (40 * 255) / 100 = 102
        // G: (30 * 255) / 100 = 76
        // B: (30 * 255) / 100 = 76
        val r = (result!!.color ushr 16) and 0xFF
        val g = (result.color ushr 8) and 0xFF
        val b = result.color and 0xFF

        assertEquals(102, r)
        assertEquals(76, g)
        assertEquals(76, b)
    }

    @Test
    fun `luminance correctly classifies light vs dark top bars`() {
        // White (#FFFFFF) is light
        val whitePixels = List(10) { 0xFFFFFFFF.toInt() }
        val whiteResult = sampler.analyzePixels(whitePixels)
        assertNotNull(whiteResult)
        assertTrue("White should be classified as light", whiteResult!!.isLight)

        // HN Orange (#FF6600) is light (relative luminance ~0.556)
        val hnPixels = List(10) { 0xFFFF6600.toInt() }
        val hnResult = sampler.analyzePixels(hnPixels)
        assertNotNull(hnResult)
        assertTrue("HN Orange should be classified as light", hnResult!!.isLight)

        // Dark background (#121212) is dark
        val darkPixels = List(10) { 0xFF121212.toInt() }
        val darkResult = sampler.analyzePixels(darkPixels)
        assertNotNull(darkResult)
        assertFalse("Dark background should be classified as dark", darkResult!!.isLight)
    }
}
