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
) {
    KAGI("Kagi", "https://kagi.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    MOJEEK("Mojeek", "https://www.mojeek.com/search?q=%s"),
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

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
