package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SearchEngine] suggestion URLs, [SearchSuggestionsFetcher] parsing,
 * and [SearchSuggestion] representations.
 */
class SearchSuggestionsTest {

    @Test
    fun `suggestUrl formats and encodes query properly for supported engines`() {
        assertEquals("https://kagi.com/api/autosuggest?q=kotlin+flow", SearchEngine.KAGI.suggestUrl("kotlin flow"))
        assertEquals("https://duckduckgo.com/ac/?q=kotlin+flow&type=list", SearchEngine.DUCKDUCKGO.suggestUrl("kotlin flow"))
        assertEquals("https://search.brave.com/api/suggest?q=kotlin+flow", SearchEngine.BRAVE.suggestUrl("kotlin flow"))
        assertEquals("https://www.startpage.com/osuggestions?q=kotlin+flow", SearchEngine.STARTPAGE.suggestUrl("kotlin flow"))
        assertEquals("https://suggestqueries.google.com/complete/search?client=chrome&q=kotlin+flow", SearchEngine.GOOGLE.suggestUrl("kotlin flow"))
        assertEquals("https://api.bing.com/osjson.aspx?query=kotlin+flow", SearchEngine.BING.suggestUrl("kotlin flow"))
        assertEquals("https://ac.ecosia.org/?q=kotlin+flow&type=list", SearchEngine.ECOSIA.suggestUrl("kotlin flow"))
    }

    @Test
    fun `mojeek suggestUrl is null`() {
        assertNull(SearchEngine.MOJEEK.suggestUrl("kotlin"))
    }

    @Test
    fun `parseOpenSearchSuggestions parses standard 2-element array`() {
        val json = """["kotlin", ["kotlin lang", "kotlin tutorial", "kotlin coroutines"]]"""
        val results = DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions(json)
        assertEquals(listOf("kotlin lang", "kotlin tutorial", "kotlin coroutines"), results)
    }

    @Test
    fun `parseOpenSearchSuggestions parses extended OpenSearch array`() {
        val json = """["kotlin", ["kotlin fresh", "kotlin kitty"], ["", ""], [], {"google:clientdata": {}}]"""
        val results = DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions(json)
        assertEquals(listOf("kotlin fresh", "kotlin kitty"), results)
    }

    @Test
    fun `parseOpenSearchSuggestions handles empty array or invalid json gracefully`() {
        assertTrue(DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions("").isEmpty())
        assertTrue(DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions("{}").isEmpty())
        assertTrue(DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions("invalid json").isEmpty())
        assertTrue(DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions("""["query", []]""").isEmpty())
        assertTrue(DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions("""["query"]""").isEmpty())
    }

    @Test
    fun `parseOpenSearchSuggestions filters out blank strings`() {
        val json = """["kotlin", ["kotlin", "  ", "", "kotlin coroutines"]]"""
        val results = DefaultSearchSuggestionsFetcher.parseOpenSearchSuggestions(json)
        assertEquals(listOf("kotlin", "kotlin coroutines"), results)
    }

    @Test
    fun `SearchSuggestion types provide correct destination and labels`() {
        val querySugg = SearchSuggestion.Query("kotlin multiplatform")
        assertEquals("kotlin multiplatform", querySugg.title)
        assertNull(querySugg.subtitle)
        assertEquals("kotlin multiplatform", querySugg.destination)

        val bookmarkSugg = SearchSuggestion.Bookmark(title = "GitHub", url = "https://github.com")
        assertEquals("GitHub", bookmarkSugg.title)
        assertEquals("https://github.com", bookmarkSugg.subtitle)
        assertEquals("https://github.com", bookmarkSugg.destination)

        val historySugg = SearchSuggestion.History(title = "Clear Browser", url = "https://clearbrowser.dev")
        assertEquals("Clear Browser", historySugg.title)
        assertEquals("https://clearbrowser.dev", historySugg.subtitle)
        assertEquals("https://clearbrowser.dev", historySugg.destination)
    }
}
