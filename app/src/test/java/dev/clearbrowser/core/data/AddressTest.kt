package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [resolveAddress] and [SearchEngine].
 *
 * The bug these guard against is quiet: a query sent to the wrong engine, or a
 * host sent to a search engine, both look like "the site is broken" rather than
 * like the address bar misread the input. Nothing crashes, so nothing tells you.
 */
class AddressTest {

    private val kagi = SearchEngine.KAGI

    @Test
    fun `a bare host becomes https`() {
        assertEquals("https://kagi.com", resolveAddress("kagi.com", kagi))
    }

    @Test
    fun `an explicit scheme is left alone`() {
        assertEquals("https://kagi.com/x", resolveAddress("https://kagi.com/x", kagi))
        assertEquals("http://localhost:8080", resolveAddress("http://localhost:8080", kagi))
        assertEquals("about:blank", resolveAddress("about:blank", kagi))
    }

    @Test
    fun `a scheme is recognised whatever its case`() {
        assertEquals("HTTPS://Kagi.com", resolveAddress("HTTPS://Kagi.com", kagi))
    }

    @Test
    fun `localhost without a port is a host`() {
        assertEquals("https://localhost", resolveAddress("localhost", kagi))
    }

    @Test
    fun `a single word is a search`() {
        val resolved = resolveAddress("kotlin", kagi)
        assertTrue("not a kagi search: $resolved", resolved!!.startsWith("https://kagi.com/search?q="))
        assertTrue(resolved.endsWith("kotlin"))
    }

    @Test
    fun `a sentence is a search, spaces and all`() {
        val resolved = resolveAddress("what is kotlin", kagi)!!
        assertTrue("spaces not encoded: $resolved", !resolved.dropWhile { it != '?' }.contains(' '))
    }

    @Test
    fun `blank input loads nothing`() {
        assertNull(resolveAddress("", kagi))
        assertNull(resolveAddress("   ", kagi))
    }

    @Test
    fun `whitespace around a host is ignored`() {
        assertEquals("https://kagi.com", resolveAddress("  kagi.com\n", kagi))
    }

    @Test
    fun `the chosen engine is the one queried`() {
        // Not a formality: the engine used to be hardcoded, so this is the
        // assertion that the setting actually reaches the address bar.
        for (engine in SearchEngine.entries) {
            val resolved = resolveAddress("kotlin", engine)!!
            assertTrue(
                "${engine.label} produced a URL for a different engine: $resolved",
                resolved.startsWith("https://"),
            )
            assertTrue(resolved.contains("kotlin"))
            assertEquals(engine.searchUrl("kotlin"), resolved)
        }
    }

    @Test
    fun `a query is encoded, not pasted in`() {
        // An unencoded '&' would end the query parameter and whatever follows
        // would be read as a second parameter.
        val resolved = kagi.searchUrl("a&b=c")
        assertTrue("unencoded separator in $resolved", !resolved.contains("&b"))
        assertTrue(resolved.contains("a%26b%3Dc"))
    }

    @Test
    fun `every engine actually receives the query`() {
        // Each template spells its query parameter differently -- `q` for most,
        // `query` for Startpage -- so this checks the substitution landed in the
        // URL rather than checking the parameter name.
        for (engine in SearchEngine.entries) {
            val resolved = engine.searchUrl("x y")
            assertTrue(
                "${engine.label} lost the query: $resolved",
                resolved.contains("x") && resolved.contains("y"),
            )
            assertTrue(
                "${engine.label} left the placeholder in: $resolved",
                !resolved.contains("%s"),
            )
        }
    }
}
