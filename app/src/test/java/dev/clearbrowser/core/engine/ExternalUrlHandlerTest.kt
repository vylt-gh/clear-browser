package dev.clearbrowser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalUrlHandlerTest {

    @Test
    fun internalWebSchemesPassToBrowser() {
        listOf(
            "https://example.com",
            "http://example.com",
            "about:blank",
            "data:text/html,<h1>Hello</h1>",
            "file:///sdcard/test.html",
            "javascript:void(0)",
            "resource://android/assets/page.html",
            "blob:https://example.com/uuid",
            "",
            "   ",
        ).forEach { url ->
            assertEquals(
                "Expected $url to pass to browser",
                ExternalUrlAction.PassToBrowser,
                ExternalUrlHandler.handle(url, canResolve = { true }),
            )
        }
    }

    @Test
    fun externalSchemeResolves() {
        val action = ExternalUrlHandler.handle("tel:+1234567890", canResolve = { true })
        assertTrue(action is ExternalUrlAction.LaunchIntent)
    }

    @Test
    fun externalSchemeUnresolvedReturnsHandledNone() {
        val action = ExternalUrlHandler.handle("tel:+1234567890", canResolve = { false })
        assertEquals(ExternalUrlAction.HandledNone, action)
    }

    @Test
    fun intentSchemeResolvesWhenAppInstalled() {
        val url = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        val action = ExternalUrlHandler.handle(url, canResolve = { true })
        assertTrue(action is ExternalUrlAction.LaunchIntent)
    }

    @Test
    fun intentSchemeUsesFallbackUrlWhenAppNotInstalled() {
        val url = "intent://video#Intent;scheme=vnd.youtube;package=com.google.android.youtube;S.browser_fallback_url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D123;end"
        val action = ExternalUrlHandler.handle(url, canResolve = { false })
        assertEquals(
            ExternalUrlAction.LoadFallback("https://www.youtube.com/watch?v=123"),
            action,
        )
    }

    @Test
    fun intentSchemeFallsBackToMarketWhenNoFallbackUrl() {
        val url = "intent://view#Intent;package=com.example.app;end"
        val action = ExternalUrlHandler.handle(url, canResolve = { false })
        assertEquals(
            ExternalUrlAction.LoadFallback("https://play.google.com/store/apps/details?id=com.example.app"),
            action,
        )
    }

    @Test
    fun marketSchemeLoadsPlayStoreWebIfMarketAppNotAvailable() {
        val url = "market://details?id=com.example.myapp"
        val action = ExternalUrlHandler.handle(url, canResolve = { false })
        assertEquals(
            ExternalUrlAction.LoadFallback("https://play.google.com/store/apps/details?id=com.example.myapp"),
            action,
        )
    }
}
