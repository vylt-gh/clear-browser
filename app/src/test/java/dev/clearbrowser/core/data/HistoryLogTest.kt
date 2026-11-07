package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The history rules, asserted without a file system.
 *
 * Each case here is a way a history stops being useful: a redirect chain turning
 * one link into five rows, a reload appending the same article forever, the
 * oldest entries growing without bound, or a `data:` URL appearing as a page the
 * browser cannot open again.
 */
class HistoryLogTest {

    private fun HistoryLog.urls() = entries.map { it.url }

    @Test
    fun `a page is stored newest first`() {
        val log = HistoryLog()
        log.record("https://a.example/one", "One", 100L)
        log.record("https://b.example/two", "Two", 200L)

        assertEquals(listOf("https://b.example/two", "https://a.example/one"), log.urls())
    }

    @Test
    fun `recording the same page moves it to the front instead of duplicating it`() {
        val log = HistoryLog()
        log.record("https://a.example/", "A", 100L)
        log.record("https://b.example/", "B", 200L)
        log.record("https://a.example/", "A again", 300L)

        assertEquals(listOf("https://a.example/", "https://b.example/"), log.urls())
        assertEquals("A again", log.entries.first().title)
        assertEquals(300L, log.entries.first().visitedAtMillis)
    }

    @Test
    fun `a fragment is part of the position, not of the page`() {
        val log = HistoryLog()
        log.record("https://a.example/doc#intro", "Doc", 100L)
        log.record("https://a.example/doc#results", "Doc", 200L)

        assertEquals(listOf("https://a.example/doc"), log.urls())
    }

    @Test
    fun `only http and https pages are recorded`() {
        val log = HistoryLog()

        assertFalse(log.record("about:blank", "Blank", 1L))
        assertFalse(log.record("data:text/html,hi", "Data", 2L))
        assertFalse(log.record("javascript:alert(1)", "Script", 3L))
        assertFalse(log.record("   ", "Nothing", 4L))
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `a blank title does not erase the one already known`() {
        val log = HistoryLog()
        log.record("https://a.example/", "The title", 100L)
        log.record("https://a.example/", "   ", 200L)

        assertEquals("The title", log.entries.single().title)
    }

    @Test
    fun `the oldest entry is dropped at the cap`() {
        val log = HistoryLog(limit = 3)
        log.record("https://a.example/1", "1", 100L)
        log.record("https://a.example/2", "2", 200L)
        log.record("https://a.example/3", "3", 300L)
        log.record("https://a.example/4", "4", 400L)

        assertEquals(
            listOf(
                "https://a.example/4",
                "https://a.example/3",
                "https://a.example/2",
            ),
            log.urls(),
        )
    }

    @Test
    fun `remove and clear change only what they name`() {
        val log = HistoryLog()
        log.record("https://a.example/", "A", 100L)
        log.record("https://b.example/", "B", 200L)

        log.remove("https://a.example/")
        assertEquals(listOf("https://b.example/"), log.urls())

        log.clear()
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `a restored log applies the same rules as a recorded one`() {
        val log = HistoryLog(
            listOf(
                HistoryEntry("https://a.example/doc#old", "Old", 100L),
                HistoryEntry("https://b.example/", "B", 200L),
                HistoryEntry("https://a.example/doc#new", "New", 300L),
                HistoryEntry("about:blank", "Not a page", 400L),
            ),
        )

        assertEquals(
            listOf("https://a.example/doc", "https://b.example/"),
            log.urls(),
        )
        assertEquals("New", log.entries.first().title)
    }
}
