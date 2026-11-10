package dev.clearbrowser.core.data

/**
 * Turn whatever came out of the address field, or out of another app's intent,
 * into something the engine can load.
 *
 * ## The rule, and why it is only two cases
 *
 * There are exactly two things a person can mean by typing into a browser's
 * address field: a page they can name, or a question they want answered.
 * Everything else is ambiguity, and the ambiguity is resolved by one question:
 * could this be a host?
 *
 * "Could be a host" is deliberately narrow. It requires *no spaces* and either
 * a dot or the literal `localhost`. That rule makes `kagi.com` a site and
 * `kagi` a search, which is the split people expect: a single word is almost
 * always a question, and a dotted word is almost always a domain. Every
 * heuristic beyond this -- a known-TLD table, a "does it resolve" DNS lookup --
 * adds a failure mode that is worse than the one it fixes, because both are
 * network calls in the path of a keystroke.
 *
 * ## Why an explicit scheme wins outright
 *
 * A URL arriving from another app is already resolved. Re-interpreting it as a
 * bare host produces `https://https//example.com`, which fails to load and
 * looks like the browser is broken rather than like it was given bad input.
 *
 * @return the URL to load, or null when there is nothing to load at all.
 */
fun resolveAddress(raw: String, engine: SearchEngine): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (SCHEMES.any { trimmed.startsWith(it, ignoreCase = true) }) return trimmed
    val looksLikeHost = !trimmed.contains(' ') &&
        (trimmed.contains('.') || trimmed.startsWith("localhost"))
    return if (looksLikeHost) "https://$trimmed" else engine.searchUrl(trimmed)
}

/**
 * Schemes that mean "this is already a URL".
 *
 * `about:` and `data:` are here because the browser itself produces them
 * (`about:newtab` on a fresh session), `file:` because local files are still a
 * legitimate thing to open, and `javascript:` because a user who types it is
 * deliberately running something and silently searching for it instead would
 * be a surprise.
 */
private val SCHEMES = listOf(
    "http://", "https://", "about:", "data:", "file:", "javascript:",
    "intent:", "market:", "mailto:", "tel:", "sms:", "geo:", "maps:",
    "moz-extension://", "resource://",
)
