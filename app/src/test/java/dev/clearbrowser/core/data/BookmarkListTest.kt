package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bookmark list rules.
 *
 * The star is the only control the list has, so the tests are about what a tap
 * on it means: save exactly one entry for a page, remove exactly the entry the
 * star is claiming, and never save something that cannot be opened again.
 */
class BookmarkListTest {

    private fun BookmarkList.urls() = entries.map { it.url }

    @Test
    fun `toggling an unsaved page saves it`() {
        val list = BookmarkList()

        assertTrue(list.toggle("https://example.com/", "Example", 100L))
        assertTrue(list.isBookmarked("https://example.com/"))
        assertEquals(1, list.entries.size)
    }

    @Test
    fun `toggling a saved page removes it`() {
        val list = BookmarkList()
        list.toggle("https://example.com/", "Example", 100L)

        assertFalse(list.toggle("https://example.com/", "Example", 200L))
        assertFalse(list.isBookmarked("https://example.com/"))
        assertTrue(list.entries.isEmpty())
    }

    @Test
    fun `new bookmarks are first`() {
        val list = BookmarkList()
        list.toggle("https://a.example/", "A", 100L)
        list.toggle("https://b.example/", "B", 200L)

        assertEquals(listOf("https://b.example/", "https://a.example/"), list.urls())
    }

    @Test
    fun `a fragment is not a second bookmark`() {
        val list = BookmarkList()
        list.toggle("https://a.example/doc#intro", "Doc", 100L)

        assertTrue(list.isBookmarked("https://a.example/doc#results"))
        assertFalse(list.toggle("https://a.example/doc#results", "Doc", 200L))
        assertTrue(list.entries.isEmpty())
    }

    @Test
    fun `a page that cannot be reopened is not bookmarked`() {
        val list = BookmarkList()

        assertFalse(list.toggle("about:blank", "Blank", 1L))
        assertFalse(list.toggle("data:text/html,hi", "Data", 2L))
        assertFalse(list.isBookmarked("javascript:alert(1)"))
        assertTrue(list.entries.isEmpty())
    }

    @Test
    fun `the title is trimmed but a title-less page is still saved`() {
        val list = BookmarkList()
        list.toggle("https://a.example/", "  A  ", 100L)
        list.toggle("https://b.example/", "", 200L)

        assertEquals("A", list.entries.last().title)
        assertEquals("", list.entries.first().title)
    }

    @Test
    fun `remove and clear change only what they name`() {
        val list = BookmarkList()
        list.toggle("https://a.example/", "A", 100L)
        list.toggle("https://b.example/", "B", 200L)

        list.remove("https://a.example/")
        assertEquals(listOf("https://b.example/"), list.urls())

        list.clear()
        assertTrue(list.entries.isEmpty())
    }

    @Test
    fun `a restored list applies the same rules as a toggled one`() {
        val list = BookmarkList(
            listOf(
                Bookmark("https://a.example/doc#old", "Old", 100L),
                Bookmark("https://b.example/", "B", 200L),
                Bookmark("https://a.example/doc#new", "New", 300L),
                Bookmark("about:blank", "Not a page", 400L),
            ),
        )

        assertEquals(
            listOf("https://a.example/doc", "https://b.example/"),
            list.urls(),
        )
        assertEquals("New", list.entries.first().title)
    }
}
