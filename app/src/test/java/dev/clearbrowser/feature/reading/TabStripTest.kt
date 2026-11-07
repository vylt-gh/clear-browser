package dev.clearbrowser.feature.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab list rules, asserted where they can be seen.
 *
 * Every case here is one that, if wrong, produces a plausible-looking browser
 * that quietly loses the page someone was reading: closing the active tab onto
 * the wrong neighbour, a background tab stealing focus, a restore that lands on
 * tab one instead of the tab that was open.
 */
class TabStripTest {

    private fun strip(vararg ids: Long, active: Long? = null) =
        TabStrip(ids.map { Tab(id = it) }, active)

    @Test
    fun `the first tab is active when none is named`() {
        val tabs = strip(1, 2, 3)
        assertEquals(1L, tabs.activeId)
        assertEquals(1L, tabs.active?.id)
    }

    @Test
    fun `a named active tab is honoured`() {
        val tabs = strip(1, 2, 3, active = 2)
        assertEquals(2L, tabs.activeId)
    }

    @Test
    fun `an active id that is not in the list falls back to the first tab`() {
        val tabs = strip(1, 2, active = 99)
        assertEquals(1L, tabs.activeId)
    }

    @Test
    fun `an empty strip has no active tab`() {
        val tabs = TabStrip()
        assertNull(tabs.activeId)
        assertNull(tabs.active)
    }

    @Test
    fun `adding a tab to an empty strip makes it active`() {
        val tabs = TabStrip()
        tabs.add(Tab(id = 7))
        assertEquals(7L, tabs.activeId)
    }

    @Test
    fun `adding a tab does not steal focus from the page being read`() {
        val tabs = strip(1, active = 1)
        tabs.add(Tab(id = 2))
        assertEquals(listOf(1L, 2L), tabs.tabs.map { it.id })
        assertEquals(1L, tabs.activeId)
    }

    @Test
    fun `selecting a tab that exists switches to it`() {
        val tabs = strip(1, 2, 3, active = 1)
        tabs.select(3)
        assertEquals(3L, tabs.activeId)
    }

    @Test
    fun `selecting a tab that was closed is ignored`() {
        val tabs = strip(1, 2, active = 2)
        tabs.select(42)
        assertEquals(2L, tabs.activeId)
    }

    @Test
    fun `closing the active tab moves to the tab that took its place`() {
        val tabs = strip(1, 2, 3, active = 2)
        val now = tabs.close(2)
        assertEquals(listOf(1L, 3L), tabs.tabs.map { it.id })
        assertEquals(3L, now)
    }

    @Test
    fun `closing the last tab in the list moves to the one before it`() {
        val tabs = strip(1, 2, 3, active = 3)
        val now = tabs.close(3)
        assertEquals(listOf(1L, 2L), tabs.tabs.map { it.id })
        assertEquals(2L, now)
    }

    @Test
    fun `closing a background tab leaves the active tab alone`() {
        val tabs = strip(1, 2, 3, active = 1)
        val now = tabs.close(3)
        assertEquals(1L, now)
        assertEquals(listOf(1L, 2L), tabs.tabs.map { it.id })
    }

    @Test
    fun `closing the only tab leaves no active tab for the caller to replace`() {
        val tabs = strip(1, active = 1)
        assertNull(tabs.close(1))
        assertTrue(tabs.tabs.isEmpty())
    }

    @Test
    fun `closing a tab that is not there is a no-op`() {
        val tabs = strip(1, 2, active = 2)
        assertEquals(2L, tabs.close(99))
        assertEquals(2, tabs.tabs.size)
    }

    @Test
    fun `updating a tab keeps its id and position`() {
        val tabs = strip(1, 2, 3)
        tabs.update(2) { it.copy(title = "Title", url = "https://example.org/") }
        assertEquals(listOf(1L, 2L, 3L), tabs.tabs.map { it.id })
        assertEquals("Title", tabs.tabs[1].title)
    }

    @Test
    fun `updating a tab that was closed is ignored`() {
        val tabs = strip(1, active = 1)
        tabs.update(99) { it.copy(title = "Nope") }
        assertEquals("", tabs.tabs.single().title)
    }

    @Test
    fun `clearing removes every tab, so a profile switch starts clean`() {
        val tabs = strip(1, 2, active = 2)
        tabs.clear()
        assertTrue(tabs.tabs.isEmpty())
        assertNull(tabs.activeId)
        assertNull(tabs.active)
    }

    @Test
    fun `summaries is a copy, so callers cannot mutate the strip`() {
        val tabs = strip(1, 2)
        val summary = tabs.summaries()
        tabs.add(Tab(id = 3))
        assertEquals(2, summary.size)
        assertEquals(3, tabs.tabs.size)
    }
}
