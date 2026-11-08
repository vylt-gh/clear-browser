package dev.clearbrowser.core.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes and decodes bookmarks for standard interoperability.
 *
 * Supports the universal Netscape Bookmark HTML format (`<!DOCTYPE NETSCAPE-Bookmark-file-1>`)
 * used by Firefox, Chrome, Safari, Brave, Edge, and desktop browsers, as well as JSON backups.
 */
object BookmarkCodec {

    fun exportToHtml(bookmarks: List<Bookmark>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file. -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n")
        sb.append("<H1>Bookmarks</H1>\n")
        sb.append("<DL><p>\n")
        for (b in bookmarks) {
            val title = escapeHtml(b.title.ifBlank { b.url })
            val addDateSeconds = b.createdAtMillis / 1000L
            sb.append("    <DT><A HREF=\"").append(escapeHtml(b.url)).append("\"")
            if (addDateSeconds > 0) {
                sb.append(" ADD_DATE=\"").append(addDateSeconds).append("\"")
            }
            sb.append(">").append(title).append("</A>\n")
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    fun importFromHtmlOrJson(content: String): List<Bookmark> {
        val trimmed = content.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val jsonList = parseJson(trimmed)
            if (jsonList.isNotEmpty()) return jsonList
        }
        return parseHtml(trimmed)
    }

    private fun parseHtml(html: String): List<Bookmark> {
        val regex = Regex("<A\\s+[^>]*HREF=[\"']([^\"']+)[\"'][^>]*>(.*?)</A>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val addDateRegex = Regex("ADD_DATE=[\"']?([0-9]+)[\"']?", RegexOption.IGNORE_CASE)
        val list = mutableListOf<Bookmark>()
        for (match in regex.findAll(html)) {
            val rawUrl = match.groupValues[1].trim()
            val rawTitle = match.groupValues[2].trim()
            val fullTag = match.value
            val title = unescapeHtml(rawTitle)
            val addDateMatch = addDateRegex.find(fullTag)
            val seconds = addDateMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val millis = if (seconds > 0) seconds * 1000L else System.currentTimeMillis()
            val normalized = normalizeWebUrl(rawUrl)
            if (normalized != null) {
                list.add(Bookmark(url = normalized, title = title, createdAtMillis = millis))
            }
        }
        return list
    }

    private fun parseJson(jsonString: String): List<Bookmark> {
        return runCatching {
            val array = if (jsonString.startsWith("[")) {
                JSONArray(jsonString)
            } else {
                val obj = JSONObject(jsonString)
                obj.optJSONArray("bookmarks") ?: JSONArray()
            }
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url")
                val normalized = normalizeWebUrl(url) ?: return@mapNotNull null
                val title = item.optString("title")
                val createdAt = item.optLong("createdAt", item.optLong("createdAtMillis", System.currentTimeMillis()))
                Bookmark(url = normalized, title = title, createdAtMillis = createdAt)
            }
        }.getOrDefault(emptyList())
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun unescapeHtml(text: String): String {
        // Strip out any inner tags e.g. <b>...</b> inside bookmark title
        val withoutTags = text.replace(Regex("<[^>]*>"), "")
        return withoutTags.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .trim()
    }
}
