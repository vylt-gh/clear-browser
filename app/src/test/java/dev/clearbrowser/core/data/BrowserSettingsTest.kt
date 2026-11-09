package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserSettingsTest {

    @Test
    fun defaultAppThemeIsSystem() {
        val settings = BrowserSettings()
        assertEquals(AppTheme.SYSTEM, settings.appTheme)
    }

    @Test
    fun appThemeValuesAreAllDistinct() {
        val themes = AppTheme.entries.toSet()
        assertEquals(3, themes.size)
        assertEquals(setOf(AppTheme.SYSTEM, AppTheme.DARK, AppTheme.LIGHT), themes)
    }

    @Test
    fun customAppThemeIsRetained() {
        val dark = BrowserSettings(appTheme = AppTheme.DARK)
        assertEquals(AppTheme.DARK, dark.appTheme)

        val light = BrowserSettings(appTheme = AppTheme.LIGHT)
        assertEquals(AppTheme.LIGHT, light.appTheme)
    }

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

    @Test
    fun defaultPullToRefreshIsTrue() {
        val settings = BrowserSettings()
        assertEquals(true, settings.pullToRefresh)
    }

    @Test
    fun customPullToRefreshIsRetained() {
        val settings = BrowserSettings(pullToRefresh = false)
        assertEquals(false, settings.pullToRefresh)
    }

    @Test
    fun defaultOnboardingCompletedIsFalse() {
        val settings = BrowserSettings()
        assertEquals(false, settings.onboardingCompleted)
    }

    @Test
    fun customOnboardingCompletedIsRetained() {
        val settings = BrowserSettings(onboardingCompleted = true)
        assertEquals(true, settings.onboardingCompleted)
    }

    @Test
    fun defaultCanopyDownloadsIsTrue() {
        val settings = BrowserSettings()
        assertEquals(true, settings.canopyDownloads)
    }

    @Test
    fun customCanopyDownloadsIsRetained() {
        val settings = BrowserSettings(canopyDownloads = false)
        assertEquals(false, settings.canopyDownloads)
    }
}
