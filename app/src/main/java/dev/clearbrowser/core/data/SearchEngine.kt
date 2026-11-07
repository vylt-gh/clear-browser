package dev.clearbrowser.core.data

import java.net.URLEncoder

/**
 * A search engine: a name, and the URL that answers a query.
 *
 * ## Why the templates live in code rather than in a preferences file
 *
 * A search engine is three facts that all have to be right together: the
 * hostname, the query parameter's spelling, and whether the site expects a
 * path or a query. Get one wrong and the browser silently searches the wrong
 * place, or worse, sends the query to a URL that 404s. Keeping them in one
 * enum means a type checker enforces that every engine has both halves, and a
 * reviewer can see all of them at once.
 *
 * ## Why these engines
 *
 * Every entry here answers a plain `GET` with no JavaScript ceremony, because
 * that is the one thing a WebView can be certain of. Google, Bing and Ecosia
 * are here because they are what people expect to find; the rest are here
 * because a browser that markets itself on privacy should not make the user
 * go hunting for a search engine that does not log them.
 *
 * [KAGI] is the default. It is the one this browser was asked to ship with.
 */
enum class SearchEngine(
    /**
     * Brand name, shown as it is written rather than translated.
     *
     * Proper nouns do not localise: "DuckDuckGo" is "DuckDuckGo" in every
     * locale, and a translation would only make the list harder to scan. This
     * is also why the labels are not string resources.
     */
    val label: String,
    /** Query URL with `%s` where the encoded query goes. */
    private val template: String,
    /**
     * OpenSearch-compatible suggestion API endpoint with `%s` where the encoded
     * query goes, or null if the engine does not provide suggestions.
     */
    private val suggestTemplate: String? = null,
) {
    KAGI(
        label = "Kagi",
        template = "https://kagi.com/search?q=%s",
        suggestTemplate = "https://kagi.com/api/autosuggest?q=%s",
    ),
    DUCKDUCKGO(
        label = "DuckDuckGo",
        template = "https://duckduckgo.com/?q=%s",
        suggestTemplate = "https://duckduckgo.com/ac/?q=%s&type=list",
    ),
    BRAVE(
        label = "Brave Search",
        template = "https://search.brave.com/search?q=%s",
        suggestTemplate = "https://search.brave.com/api/suggest?q=%s",
    ),
    STARTPAGE(
        label = "Startpage",
        template = "https://www.startpage.com/sp/search?query=%s",
        suggestTemplate = "https://www.startpage.com/osuggestions?q=%s",
    ),
    GOOGLE(
        label = "Google",
        template = "https://www.google.com/search?q=%s",
        suggestTemplate = "https://suggestqueries.google.com/complete/search?client=chrome&q=%s",
    ),
    BING(
        label = "Bing",
        template = "https://api.bing.com/osjson.aspx?query=%s",
        suggestTemplate = "https://api.bing.com/osjson.aspx?query=%s",
    ),
    ECOSIA(
        label = "Ecosia",
        template = "https://www.ecosia.org/search?q=%s",
        suggestTemplate = "https://ac.ecosia.org/?q=%s&type=list",
    ),
    MOJEEK(
        label = "Mojeek",
        template = "https://www.mojeek.com/search?q=%s",
        suggestTemplate = null,
    ),
    ;

    /**
     * The URL that searches this engine for [query].
     *
     * The query is percent-encoded as a *form* value, so a space becomes `+`.
     * That is correct in a query string and it is what these sites expect:
     * `%20` works too, but `+` is shorter and is what the sites themselves
     * emit, so it is the spelling least likely to hit an edge case on an
     * engine that parses its own input loosely.
     */
    fun searchUrl(query: String): String = template.replace("%s", encode(query))

    /**
     * The URL that requests OpenSearch completion suggestions for [query],
     * or null if this engine does not support a suggestions API.
     */
    fun suggestUrl(query: String): String? = suggestTemplate?.replace("%s", encode(query))

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
