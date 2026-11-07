package dev.clearbrowser.core.engine

import android.webkit.URLUtil
import java.net.URLDecoder

internal object DownloadFilenameResolver {
    private const val EXTENDED = "filename*"
    private const val PLAIN = "filename"

    fun resolve(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
    ): String {
        val fromDisposition = parseDispositionFilename(contentDisposition)
        if (!fromDisposition.isNullOrBlank()) return fromDisposition
        return URLUtil.guessFileName(url, contentDisposition, mimeType)
    }

    private fun parseDispositionFilename(contentDisposition: String?): String? {
        val header = contentDisposition?.takeIf { it.isNotBlank() } ?: return null
        header.split(';').forEach { part ->
            val param = part.trim()
            if (param.startsWith(EXTENDED, ignoreCase = true)) {
                val value = param.substringAfter("''", "").trim()
                if (value.isNotBlank()) {
                    val decoded = runCatching { URLDecoder.decode(value, "UTF-8") }
                        .getOrDefault(value)
                    val name = decoded.substringAfterLast('/').substringAfterLast('\\')
                    if (name.isNotBlank()) return name
                }
            }
        }
        header.split(';').forEach { part ->
            val param = part.trim()
            if (param.startsWith(PLAIN, ignoreCase = true)) {
                val value = param.substringAfter('=', "").trim().trim('"').trim('\'')
                val name = value.substringAfterLast('/').substringAfterLast('\\')
                if (name.isNotBlank()) return name
            }
        }
        return null
    }
}
