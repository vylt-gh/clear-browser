package dev.clearbrowser.core.engine

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

object ClearGeckoRuntime {
    private const val TAG = "ClearGeckoRuntime"
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    private const val UBLOCK_URI = "resource://android/assets/extensions/ublock/"

    @Volatile
    private var instance: GeckoRuntime? = null

    @Volatile
    var uBlockExtension: WebExtension? = null
        private set

    @Volatile
    var uBlockOptionsUrl: String? = null
        private set

    fun get(context: Context): GeckoRuntime {
        return instance ?: synchronized(this) {
            instance ?: createRuntime(context.applicationContext).also { instance = it }
        }
    }

    fun setPreferredColorScheme(scheme: Int) {
        instance?.settings?.setPreferredColorScheme(scheme)
    }

    fun getUBlockDashboardUrl(callback: (String?) -> Unit) {
        val cached = uBlockOptionsUrl
        if (!cached.isNullOrBlank()) {
            callback(cached)
            return
        }
        val rt = instance
        if (rt == null) {
            callback(null)
            return
        }
        rt.webExtensionController.list().accept(
            { list ->
                val ubo = list?.find { it.id == UBLOCK_ID }
                if (ubo != null) {
                    uBlockExtension = ubo
                    val url = ubo.metaData.optionsPageUrl
                        ?: ubo.metaData.baseUrl.let { if (it.endsWith("/")) "${it}dashboard.html" else "$it/dashboard.html" }
                    if (!url.isNullOrBlank()) {
                        uBlockOptionsUrl = url
                    }
                    callback(url)
                } else {
                    callback(null)
                }
            },
            { error ->
                Log.e(TAG, "Failed to list extensions", error)
                callback(null)
            },
        )
    }

    private fun createRuntime(context: Context): GeckoRuntime {
        val settings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                    .cookiePurging(true)
                    .queryParameterStrippingEnabled(true)
                    .build(),
            )
            .build()

        val runtime = GeckoRuntime.create(context, settings)

        // Listen for extension readiness to capture optionsPageUrl / baseUrl
        runtime.webExtensionController.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onReady(extension: WebExtension) {
                Log.i(TAG, "Extension onReady: ${extension.id}, options=${extension.metaData.optionsPageUrl}, base=${extension.metaData.baseUrl}")
                if (extension.id == UBLOCK_ID) {
                    uBlockExtension = extension
                    val url = extension.metaData.optionsPageUrl
                        ?: extension.metaData.baseUrl.let { if (it.endsWith("/")) "${it}dashboard.html" else "$it/dashboard.html" }
                    if (!url.isNullOrBlank()) {
                        uBlockOptionsUrl = url
                        Log.i(TAG, "uBlock dashboard URL resolved: $url")
                    }
                }
            }
        })

        // Register bundled uBlock Origin as a built-in extension
        runtime.webExtensionController
            .ensureBuiltIn(UBLOCK_URI, UBLOCK_ID)
            .accept(
                { extension ->
                    Log.i(TAG, "uBlock Origin registered: ${extension?.id}, options=${extension?.metaData?.optionsPageUrl}, base=${extension?.metaData?.baseUrl}")
                    if (extension != null) {
                        uBlockExtension = extension
                        runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                        val url = extension.metaData.optionsPageUrl
                            ?: extension.metaData.baseUrl.let { if (it.endsWith("/")) "${it}dashboard.html" else "$it/dashboard.html" }
                        if (!url.isNullOrBlank()) {
                            uBlockOptionsUrl = url
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to register uBlock Origin", error)
                },
            )

        return runtime
    }
}
