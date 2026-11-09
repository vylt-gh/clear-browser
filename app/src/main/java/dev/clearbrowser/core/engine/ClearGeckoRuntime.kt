package dev.clearbrowser.core.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** Metadata for an installed WebExtension displayed in the UI. */
data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isBuiltIn: Boolean,
    val isEnabled: Boolean,
    val optionsUrl: String?,
    val baseUrl: String?,
)

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

    private val _installedExtensions = MutableStateFlow<List<ExtensionInfo>>(emptyList())
    val installedExtensions: StateFlow<List<ExtensionInfo>> = _installedExtensions.asStateFlow()

    private val registeredExtensions = mutableMapOf<String, WebExtension>()

    fun getExtension(id: String): WebExtension? = registeredExtensions[id]

    fun getRegisteredExtensions(): Collection<WebExtension> = registeredExtensions.values

    fun listExtensions(runtime: GeckoRuntime? = instance): GeckoResult<List<WebExtension>> {
        val rt = runtime ?: instance ?: return GeckoResult.fromValue(emptyList())
        return rt.webExtensionController.list()
    }

    fun refreshExtensions(runtime: GeckoRuntime? = instance) {
        val rt = runtime ?: instance ?: return
        rt.webExtensionController.list().accept(
            { list ->
                val items = list?.map { ext ->
                    registeredExtensions[ext.id] = ext
                    val optionsUrl = ext.metaData.optionsPageUrl
                        ?: if (ext.id == UBLOCK_ID) {
                            ext.metaData.baseUrl.let { if (it.endsWith("/")) "${it}dashboard.html" else "$it/dashboard.html" }
                        } else null

                    ExtensionInfo(
                        id = ext.id,
                        name = ext.metaData.name?.ifBlank { null } ?: if (ext.id == UBLOCK_ID) "uBlock Origin" else ext.id,
                        version = ext.metaData.version.orEmpty(),
                        description = ext.metaData.description.orEmpty(),
                        isBuiltIn = ext.isBuiltIn || ext.id == UBLOCK_ID,
                        isEnabled = ext.metaData.enabled,
                        optionsUrl = optionsUrl,
                        baseUrl = ext.metaData.baseUrl,
                    )
                }.orEmpty()
                _installedExtensions.value = items
            },
            { err -> Log.e(TAG, "Failed to refresh extensions", err) },
        )
    }

    fun uninstallExtension(id: String) {
        val rt = instance ?: return
        val ext = registeredExtensions[id] ?: return
        rt.webExtensionController.uninstall(ext).accept(
            { refreshExtensions() },
            { err -> Log.e(TAG, "Failed to uninstall extension $id", err) },
        )
    }

    fun get(context: Context): GeckoRuntime {
        return instance ?: synchronized(this) {
            instance ?: createRuntime(context.applicationContext).also { instance = it }
        }
    }

    val geckoViewVersion: String
        get() = runCatching {
            val ver = org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION
            val buildId = org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID
            if (!buildId.isNullOrBlank()) "$ver ($buildId)" else ver
        }.getOrDefault("144.0")

    fun revokePermissionFromHost(origin: String) {
        val host = runCatching { android.net.Uri.parse(origin).host }.getOrNull() ?: origin
        instance?.storageController?.clearDataFromHost(
            host,
            org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS or
                org.mozilla.geckoview.StorageController.ClearFlags.SITE_SETTINGS,
        )
    }

    fun clearAllPermissions() {
        instance?.storageController?.clearData(
            org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS or
                org.mozilla.geckoview.StorageController.ClearFlags.SITE_SETTINGS,
        )
    }

    fun setPreferredColorScheme(scheme: Int) {
        instance?.settings?.setPreferredColorScheme(scheme)
    }

    fun setFontSizeFactor(factor: Float) {
        instance?.settings?.fontSizeFactor = factor
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
            .extensionsWebAPIEnabled(true)
            .loginAutofillEnabled(true)
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

        // Delegate for prompt confirmations during extension installation from AMO or file
        runtime.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                data: Array<out String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                Log.i(TAG, "Extension install requested: ${extension.id} (${extension.metaData.name})")
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(
                        true, // isPermissionsGranted
                        true, // isPrivateModeGranted
                        false, // isTechnicalAndInteractionDataGranted
                    ),
                )
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                data: Array<out String>,
            ): GeckoResult<AllowOrDeny>? {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                data: Array<out String>,
            ): GeckoResult<AllowOrDeny>? {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }

        // Listen for extension readiness and install/uninstall events
        runtime.webExtensionController.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                Log.i(TAG, "Extension onInstalled: ${extension.id}")
                refreshExtensions(runtime)
            }

            override fun onUninstalled(extension: WebExtension) {
                Log.i(TAG, "Extension onUninstalled: ${extension.id}")
                registeredExtensions.remove(extension.id)
                refreshExtensions(runtime)
            }

            override fun onEnabled(extension: WebExtension) {
                refreshExtensions(runtime)
            }

            override fun onDisabled(extension: WebExtension) {
                refreshExtensions(runtime)
            }

            override fun onReady(extension: WebExtension) {
                Log.i(TAG, "Extension onReady: ${extension.id}, options=${extension.metaData.optionsPageUrl}, base=${extension.metaData.baseUrl}")
                registeredExtensions[extension.id] = extension
                if (extension.id == UBLOCK_ID) {
                    uBlockExtension = extension
                    val url = extension.metaData.optionsPageUrl
                        ?: extension.metaData.baseUrl.let { if (it.endsWith("/")) "${it}dashboard.html" else "$it/dashboard.html" }
                    if (!url.isNullOrBlank()) {
                        uBlockOptionsUrl = url
                        Log.i(TAG, "uBlock dashboard URL resolved: $url")
                    }
                }
                refreshExtensions(runtime)
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
                        registeredExtensions[extension.id] = extension
                        runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                        val url = extension.metaData.optionsPageUrl
                            ?: extension.metaData.baseUrl.let { if (it.endsWith("/")) "${it}dashboard.html" else "$it/dashboard.html" }
                        if (!url.isNullOrBlank()) {
                            uBlockOptionsUrl = url
                        }
                    }
                    refreshExtensions(runtime)
                },
                { error ->
                    Log.e(TAG, "Failed to register uBlock Origin", error)
                },
            )

        return runtime
    }
}
