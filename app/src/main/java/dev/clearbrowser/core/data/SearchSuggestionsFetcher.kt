package dev.clearbrowser.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

interface SearchSuggestionsFetcher {
    suspend fun fetch(engine: SearchEngine, query: String): List<String>
}

object DefaultSearchSuggestionsFetcher : SearchSuggestionsFetcher {
    override suspend fun fetch(engine: SearchEngine, query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val urlString = engine.suggestUrl(trimmed) ?: return@withContext emptyList()
        try {
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                )
                setRequestProperty("Accept", "application/json,application/x-suggestions+json,*/*")
            }
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseOpenSearchSuggestions(body)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseOpenSearchSuggestions(jsonString: String): List<String> {
        return try {
            val array = JSONArray(jsonString)
            val suggestions = array.optJSONArray(1) ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until suggestions.length()) {
                val item = suggestions.optString(i)
                if (!item.isNullOrBlank()) {
                    list.add(item)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
