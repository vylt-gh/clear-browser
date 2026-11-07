package dev.clearbrowser.core.engine

import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Which storage partition a session's WebView belongs to.
 *
 * WebView 153 supports multiple profiles, each with its own cookies, storage,
 * cache, and permissions. That is the correct foundation for incognito: a
 * separate profile means a private window genuinely cannot see or write the
 * normal window's state, rather than relying on remembering to scrub
 * everything afterwards. "Clear it on exit" is a promise the OS can break;
 * "it was never stored there" is not.
 */
enum class BrowserProfile(
    /**
     * The WebView profile name.
     *
     * `null` means the default profile, which is the one every other WebView in
     * the process shares. Namespaced with a scheme-ish prefix so it cannot
     * collide with a profile name used by anything else in the process.
     */
    internal val profileName: String?,
) {
    DEFAULT(null),
    INCOGNITO("dev.clearbrowser.incognito"),
    ;

    val isIncognito: Boolean get() = this == INCOGNITO

    companion object {
        /** Whether this WebView build supports profiles at all. */
        val isMultiProfileSupported: Boolean
            get() = runCatching {
                WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
            }.getOrDefault(false)

        /**
         * Assign [webView] to this profile.
         *
         * Must happen before the view loads anything, which is why a profile
         * change means a new WebView rather than a property flip.
         *
         * The result is verified rather than assumed. `setProfile` can fail on a
         * build that advertises `MULTI_PROFILE` but refuses the call, and a
         * silent failure here is the worst kind of bug this codebase could have:
         * the window would say "Private" while writing cookies into the normal
         * jar. So the outcome is logged and re-read from the view, and the caller
         * is told which profile it actually got.
         */
        internal fun applyTo(webView: android.webkit.WebView, profile: BrowserProfile): Boolean {
            val name = profile.profileName
            if (name == null) return true
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                Log.w(TAG, "MULTI_PROFILE unsupported; $profile cannot be isolated")
                return false
            }
            return runCatching { WebViewCompat.setProfile(webView, name) }
                .fold(
                    onSuccess = {
                        val actual = of(webView)
                        val ok = actual == profile
                        if (!ok) {
                            Log.w(TAG, "setProfile($name) did not take; view is in $actual")
                        } else {
                            Log.i(TAG, "WebView in profile $profile")
                        }
                        ok
                    },
                    onFailure = {
                        Log.w(TAG, "setProfile($name) threw; private browsing unavailable", it)
                        false
                    },
                )
        }

        /**
         * The profile a view currently belongs to, or null when the build
         * cannot say.
         */
        internal fun of(webView: android.webkit.WebView): BrowserProfile? {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return null
            val name = runCatching { WebViewCompat.getProfile(webView)?.name }.getOrNull()
                ?: return null
            return entries.firstOrNull { it.profileName == name }
        }

        private const val TAG = "BrowserProfile"
    }
}
