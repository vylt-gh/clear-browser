package dev.clearbrowser.feature.reading

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the browser's own new tab page.
 *
 * The rule these guard is one string comparison wide and it is easy to get
 * wrong in a way nobody sees: the new tab page has to be recognised as such
 * on a cold boot, in a restored session, and after a page navigates to the
 * engine's blank document. Get it wrong and the browser opens on a blank
 * screen with an address bar full of `about:blank`, or -- worse -- treats the
 * engine's empty document as a page the user asked for.
 */
class StartPageUrlTest {

    @Test
    fun `the browser's new tab page is its own URL`() {
        assertEquals("about:newtab", ABOUT_NEWTAB)
    }

    @Test
    fun `the new tab page is recognised`() {
        assertTrue(isStartPageUrl(ABOUT_NEWTAB))
    }

    @Test
    fun `a tab with no URL yet is the new tab page`() {
        assertTrue(isStartPageUrl(""))
        assertTrue(isStartPageUrl("   "))
        assertTrue(isStartPageUrl(null))
    }

    @Test
    fun `the engine's blank document is still the new tab page`() {
        // A session written by an older build recorded the engine's blank
        // document as the tab's page. It has to keep being recognised, or an
        // upgrading install opens on a page that looks broken.
        assertTrue(isStartPageUrl(ABOUT_BLANK))
    }

    @Test
    fun `a real page is not the new tab page`() {
        assertFalse(isStartPageUrl("https://example.com"))
        assertFalse(isStartPageUrl("http://example.com/page"))
        assertFalse(isStartPageUrl("about:config"))
        assertFalse(isStartPageUrl("data:text/html,<h1>hi</h1>"))
    }

    @Test
    fun `the state calls the new tab page the start page`() {
        assertTrue(ReadingUiState(url = ABOUT_NEWTAB).isStartPage)
        assertTrue(ReadingUiState(url = "").isStartPage)
        assertTrue(ReadingUiState(url = ABOUT_BLANK).isStartPage)
        assertFalse(ReadingUiState(url = "https://example.com").isStartPage)
    }

    @Test
    fun `the start page offers nothing to bookmark or share`() {
        val start = ReadingUiState(url = ABOUT_NEWTAB)
        assertFalse(start.canBookmark)
        assertFalse(start.canGoForward)
    }
}

/**
 * Tests for where the reload menu is put.
 *
 * The menu hangs off the reload button, and the button moves: the chrome can
 * be docked anywhere along the bottom of the screen. A menu that is anchored
 * to a fixed distance from the edge of the window would end up pointing at
 * nothing as soon as the chrome was moved, so the arrow is placed on the
 * button itself.
 */
class ReloadPopupPositionProviderTest {

    private val density = Density(density = 2f, fontScale = 1f)

    private fun provider() = ReloadPopupPositionProvider(density)

    @Test
    fun `the arrow lands on the button`() {
        // A 38dp button, 76px at this density, with room for the menu to its
        // left.
        val anchor = IntRect(left = 700, top = 1900, right = 776, bottom = 1976)
        val popup = IntSize(width = 480, height = 200)

        val position = provider().calculatePosition(
            anchorBounds = anchor,
            windowSize = IntSize(1080, 2400),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popup,
        )

        // The tip lands on the button: the arrow sits 16dp in from the
        // bubble's right edge and its tip is half an arrow (8dp) further in,
        // so the bubble's right edge is 24dp right of the button's centre,
        // at 738 + 48 = 786.
        assertEquals(786 - 480, position.x)
        assertEquals(1900 - 200 - 12, position.y)
    }

    @Test
    fun `the menu stays on the screen`() {
        // A button hard against the right edge, with a menu wider than the
        // space left for it.
        val anchor = IntRect(left = 1040, top = 1900, right = 1076, bottom = 1976)
        val popup = IntSize(width = 480, height = 200)

        val position = provider().calculatePosition(
            anchorBounds = anchor,
            windowSize = IntSize(1080, 2400),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popup,
        )

        val margin = 12 * 2 // 12dp at this density
        assertTrue("menu ran off the right edge: ${position.x}", position.x <= 1080 - 480 - margin)
        assertTrue("menu ran off the left edge: ${position.x}", position.x >= margin)
    }
}
