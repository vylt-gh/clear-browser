package dev.clearbrowser.core.engine

import android.content.Intent
import android.net.Uri

sealed interface ExternalUrlAction {
    /** The URL is an internal browser URL (http, https, about, etc.) and should be handled by the engine. */
    data object PassToBrowser : ExternalUrlAction

    /** The URL resolved to an external Activity intent and should be started. */
    data class LaunchIntent(val intent: Intent) : ExternalUrlAction

    /** The external intent failed or was not found, but provided a web fallback URL. */
    data class LoadFallback(val fallbackUrl: String) : ExternalUrlAction

    /** The URL could not be handled externally and has no fallback. */
    data object HandledNone : ExternalUrlAction
}

object ExternalUrlHandler {
    private val INTERNAL_SCHEMES = setOf(
        "http", "https", "about", "data", "file", "javascript", "resource", "blob", "moz-extension"
    )

    fun handle(
        url: String,
        canResolve: (Intent) -> Boolean,
    ): ExternalUrlAction {
        if (url.isBlank()) return ExternalUrlAction.PassToBrowser
        val scheme = url.substringBefore(':', "").lowercase()

        if (scheme in INTERNAL_SCHEMES) {
            return ExternalUrlAction.PassToBrowser
        }

        if (scheme == "intent") {
            var intent: Intent? = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)?.apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                null
            }

            // Fallback parser if running in tests or if Intent.parseUri is unmocked
            val parsedExtras = parseIntentUri(url)
            val fallbackUrl = intent?.getStringExtra("browser_fallback_url")
                ?: parsedExtras.fallbackUrl
            val packageName = intent?.`package`
                ?: parsedExtras.packageName

            if (intent == null) {
                val dataUri = parsedExtras.dataUri
                intent = if (!dataUri.isNullOrBlank()) {
                    Intent(Intent.ACTION_VIEW, Uri.parse(dataUri))
                } else {
                    Intent(Intent.ACTION_VIEW)
                }.apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!packageName.isNullOrBlank()) {
                        setPackage(packageName)
                    }
                }
            }

            if (canResolve(intent)) {
                return ExternalUrlAction.LaunchIntent(intent)
            }

            if (!fallbackUrl.isNullOrBlank() && (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))) {
                return ExternalUrlAction.LoadFallback(fallbackUrl)
            }

            if (!packageName.isNullOrBlank()) {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return if (canResolve(marketIntent)) {
                    ExternalUrlAction.LaunchIntent(marketIntent)
                } else {
                    ExternalUrlAction.LoadFallback("https://play.google.com/store/apps/details?id=$packageName")
                }
            }

            return ExternalUrlAction.HandledNone
        }

        // Standard external schemes: mailto, tel, sms, market, geo, maps, etc.
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (canResolve(intent)) {
                ExternalUrlAction.LaunchIntent(intent)
            } else if (scheme == "market") {
                val pkg = runCatching { Uri.parse(url).getQueryParameter("id") }.getOrNull()
                    ?: url.substringAfter("id=", "").substringBefore('&').takeIf { it.isNotBlank() }
                if (!pkg.isNullOrBlank()) {
                    ExternalUrlAction.LoadFallback("https://play.google.com/store/apps/details?id=$pkg")
                } else {
                    ExternalUrlAction.HandledNone
                }
            } else {
                ExternalUrlAction.HandledNone
            }
        } catch (e: Exception) {
            ExternalUrlAction.HandledNone
        }
    }

    data class ParsedIntentData(
        val packageName: String? = null,
        val fallbackUrl: String? = null,
        val dataUri: String? = null,
    )

    fun parseIntentUri(url: String): ParsedIntentData {
        var packageName: String? = null
        var fallbackUrl: String? = null
        var scheme: String? = null

        val fragmentIndex = url.indexOf("#Intent;")
        val rawUriPart = if (fragmentIndex != -1) url.substring(0, fragmentIndex) else url
        val intentParams = if (fragmentIndex != -1) url.substring(fragmentIndex + "#Intent;".length) else ""

        for (param in intentParams.split(';')) {
            val trimmed = param.trim()
            when {
                trimmed.startsWith("package=") -> packageName = trimmed.substringAfter("package=")
                trimmed.startsWith("scheme=") -> scheme = trimmed.substringAfter("scheme=")
                trimmed.startsWith("S.browser_fallback_url=") -> {
                    val raw = trimmed.substringAfter("S.browser_fallback_url=")
                    fallbackUrl = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                }
                trimmed.startsWith("browser_fallback_url=") -> {
                    val raw = trimmed.substringAfter("browser_fallback_url=")
                    fallbackUrl = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                }
            }
        }

        val dataUri = if (rawUriPart.startsWith("intent://")) {
            val afterIntent = rawUriPart.removePrefix("intent://")
            val effectiveScheme = scheme ?: "https"
            "$effectiveScheme://$afterIntent"
        } else if (rawUriPart.startsWith("intent:")) {
            val afterIntent = rawUriPart.removePrefix("intent:")
            val effectiveScheme = scheme ?: ""
            if (effectiveScheme.isNotBlank()) "$effectiveScheme:$afterIntent" else null
        } else null

        return ParsedIntentData(
            packageName = packageName,
            fallbackUrl = fallbackUrl,
            dataUri = dataUri,
        )
    }
}
