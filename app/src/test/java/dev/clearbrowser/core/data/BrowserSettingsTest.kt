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
}
