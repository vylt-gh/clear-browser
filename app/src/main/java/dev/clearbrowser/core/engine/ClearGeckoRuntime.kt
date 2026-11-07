package dev.clearbrowser.core.engine

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object ClearGeckoRuntime {
    private const val TAG = "ClearGeckoRuntime"
    private const val UBLOCK_ID = "uBlock0@raymondhill.net"
    private const val UBLOCK_URI = "resource://android/assets/extensions/ublock/"

    @Volatile
    private var instance: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        return instance ?: synchronized(this) {
            instance ?: createRuntime(context.applicationContext).also { instance = it }
        }
    }

    fun setPreferredColorScheme(scheme: Int) {
        instance?.settings?.setPreferredColorScheme(scheme)
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

        // Register bundled uBlock Origin as a built-in extension
        runtime.webExtensionController
            .ensureBuiltIn(UBLOCK_URI, UBLOCK_ID)
            .accept(
                { extension ->
                    Log.i(TAG, "uBlock Origin registered: ${extension?.id}")
                    if (extension != null) {
                        runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to register uBlock Origin", error)
                },
            )

        return runtime
    }
}
