package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserSettingsTest {

    @Test
    fun defaultWebThemeIsSystem() {
        val settings = BrowserSettings()
        assertEquals(WebTheme.SYSTEM, settings.webTheme)
    }

    @Test
    fun webThemeValuesAreAllDistinct() {
        val themes = WebTheme.entries.toSet()
        assertEquals(3, themes.size)
        assertEquals(setOf(WebTheme.SYSTEM, WebTheme.DARK, WebTheme.LIGHT), themes)
    }

    @Test
    fun defaultTextZoomIs100Percent() {
        val settings = BrowserSettings()
        assertEquals(100, settings.textZoom)
    }

    @Test
    fun customTextZoomIsRetained() {
        val settings = BrowserSettings(textZoom = 150)
        assertEquals(150, settings.textZoom)
    }

    @Test
    fun defaultChromeThemeIsClear() {
        val settings = BrowserSettings()
        assertEquals(ChromeTheme.CLEAR, settings.chromeTheme)
    }

    @Test
    fun customChromeThemeIsRetained() {
        val acrylic = BrowserSettings(chromeTheme = ChromeTheme.ACRYLIC)
        assertEquals(ChromeTheme.ACRYLIC, acrylic.chromeTheme)

        val material = BrowserSettings(chromeTheme = ChromeTheme.MATERIAL)
        assertEquals(ChromeTheme.MATERIAL, material.chromeTheme)
    }

    @Test
    fun chromeThemeEntriesAreAllDistinct() {
        val themes = ChromeTheme.entries.toSet()
        assertEquals(3, themes.size)
        assertEquals(setOf(ChromeTheme.CLEAR, ChromeTheme.ACRYLIC, ChromeTheme.MATERIAL), themes)
    }

    @Test
    fun defaultHideHandleIsFalse() {
        val settings = BrowserSettings()
        assertEquals(false, settings.hideHandle)
    }

    @Test
    fun customHideHandleIsRetained() {
        val settings = BrowserSettings(hideHandle = true)
        assertEquals(true, settings.hideHandle)
    }
}
