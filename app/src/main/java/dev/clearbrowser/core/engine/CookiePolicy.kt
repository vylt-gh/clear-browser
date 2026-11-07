package dev.clearbrowser.core.engine

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.util.Log

/**
 * Cookie and session handling.
 *
 * This is the layer that fails *silently*, which makes it far more dangerous
 * than it looks. Nothing throws, nothing renders wrong, the page just quietly
 * misbehaves in ways that are painful to diagnose:
 *
 *  - **Third-party cookies are off by default.** `setAcceptThirdPartyCookies`
 *    defaults to `false` for every app targeting Lollipop or later. Leaving it
 *    alone breaks embedded payment frames, OAuth popups, comment widgets, and
 *    cross-domain media, each of which presents as "this site is broken" with
 *    no visible cause. A browser is not a single-origin app, so the restrictive
 *    default is the wrong default here.
 *  - **`flush()` is never called automatically.** Cookies live in memory until
 *    the process decides otherwise, and a browser process is a routine
 *    Android-kill target. Without a flush on the way to the background, session
 *    cookies are lost exactly when the user backgrounds the app to switch to
 *    the thing they were logging into.
 *
 * ## Why the CookieManager is looked up per profile
 *
 * `CookieManager.getInstance()` is the *default* profile's manager. Using it
 * for an incognito WebView would write private cookies into the very jar
 * incognito exists to avoid, and would leave them behind after the window
 * closed. The profile's own manager is the only correct one.
 */
class CookiePolicy(private val webView: WebView) {

    /**
     * The manager for this WebView's profile, falling back to the default when
     * the build does not support profiles.
     *
     * A build without multi-profile has nowhere to put a private session
     * except the shared jar, so the fallback is the only option available; it is
     * left explicit rather than silently wrong.
     */
    private val manager: CookieManager
        get() {
            // Per-profile first: using the default manager for a private window
            // would write its cookies into the very jar it exists to avoid.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching { WebViewCompat.getProfile(webView)?.cookieManager }
                    .getOrNull()
                    ?.let { return it }
            }
            return CookieManager.getInstance()
        }

    fun apply() {
        manager.setAcceptCookie(true)
        // Per-WebView, and off by default. Must be set every time a WebView is
        // created, not once per process.
        runCatching { manager.setAcceptThirdPartyCookies(webView, true) }
        // Read it back rather than trusting the setter: this flag has a long
        // history of silently not taking effect, and a browser that silently
        // breaks embedded payment frames is worse than one that logs.
        Log.i(
            TAG,
            "cookies: accept=${runCatching { manager.acceptCookie() }.getOrNull()} " +
                "thirdParty=${runCatching { manager.acceptThirdPartyCookies(webView) }.getOrNull()}",
        )
    }

    /**
     * Persist cookies to disk.
     *
     * Synchronous, because the caller is usually `onStop` and there is no
     * guarantee the process will survive an async write.
     */
    fun flush() {
        runCatching { manager.flush() }
    }

    fun clearAll() {
        runCatching {
            manager.removeAllCookies(null)
            manager.flush()
        }
    }

    private companion object {
        const val TAG = "ClearBrowser"
    }
}
