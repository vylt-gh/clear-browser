package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkCodecTest {

    @Test
    fun exportToHtml_containsNetscapeHeaderAndBookmarks() {
        val bookmarks = listOf(
            Bookmark(url = "https://kotlinlang.org", title = "Kotlin & Coroutines", createdAtMillis = 1730000000000L),
            Bookmark(url = "https://en.wikipedia.org", title = "Wikipedia <Main>", createdAtMillis = 1731000000000L),
        )
        val html = BookmarkCodec.exportToHtml(bookmarks)

        assertTrue(html.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("HREF=\"https://kotlinlang.org\""))
        assertTrue(html.contains("Kotlin &amp; Coroutines"))
        assertTrue(html.contains("Wikipedia &lt;Main&gt;"))
        assertTrue(html.contains("ADD_DATE=\"1730000000\""))
    }

    @Test
    fun importFromHtml_parsesNetscapeBookmarkHtml() {
        val sampleHtml = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p>
                <DT><A HREF="https://developer.mozilla.org/en-US/" ADD_DATE="1700000000">MDN Web Docs</A>
                <DT><A HREF="https://github.com/vylt-gh/clear-browser">Clear Browser &amp; Repo</A>
            </DL><p>
        """.trimIndent()

        val parsed = BookmarkCodec.importFromHtmlOrJson(sampleHtml)
        assertEquals(2, parsed.size)
        assertEquals("https://developer.mozilla.org/en-US/", parsed[0].url)
        assertEquals("MDN Web Docs", parsed[0].title)
        assertEquals(1700000000000L, parsed[0].createdAtMillis)

        assertEquals("https://github.com/vylt-gh/clear-browser", parsed[1].url)
        assertEquals("Clear Browser & Repo", parsed[1].title)
    }

    @Test
    fun importFromJson_parsesJsonArray() {
        val json = """
            [
                {"url": "https://news.ycombinator.com", "title": "Hacker News", "createdAt": 1720000000000},
                {"url": "https://archlinux.org", "title": "Arch Linux"}
            ]
        """.trimIndent()

        val parsed = BookmarkCodec.importFromHtmlOrJson(json)
        assertEquals(2, parsed.size)
        assertEquals("https://news.ycombinator.com", parsed[0].url)
        assertEquals("Hacker News", parsed[0].title)
        assertEquals(1720000000000L, parsed[0].createdAtMillis)
        assertEquals("https://archlinux.org", parsed[1].url)
        assertEquals("Arch Linux", parsed[1].title)
    }

    @Test
    fun bookmarkList_import_deduplicatesAndMerges() {
        val list = BookmarkList(
            listOf(Bookmark(url = "https://kotlinlang.org", title = "Kotlin"))
        )
        val incoming = listOf(
            Bookmark(url = "https://kotlinlang.org", title = "Kotlin Lang"),
            Bookmark(url = "https://github.com", title = "GitHub"),
            Bookmark(url = "javascript:alert(1)", title = "Malicious Scheme"),
        )
        val added = list.import(incoming)
        assertEquals(1, added)
        assertEquals(2, list.entries.size)
        assertTrue(list.entries.any { it.url == "https://github.com" })
    }
}
