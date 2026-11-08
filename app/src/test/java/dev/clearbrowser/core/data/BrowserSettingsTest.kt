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
    fun defaultOriginalMaterial3ChromeIsFalse() {
        val settings = BrowserSettings()
        assertEquals(false, settings.originalMaterial3Chrome)
    }

    @Test
    fun customOriginalMaterial3ChromeIsRetained() {
        val settings = BrowserSettings(originalMaterial3Chrome = true)
        assertEquals(true, settings.originalMaterial3Chrome)
    }
}
