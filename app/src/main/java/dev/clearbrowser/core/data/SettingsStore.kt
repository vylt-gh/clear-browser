package dev.clearbrowser.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the chrome sits. Three options, because one is a bet, not a decision. */
enum class ChromeLayout {
    /** A single floating pill: back, address, menu. The iOS 26 default. */
    COMPACT,

    /** Pill on top, a second row of controls beneath. The iOS 18 layout. */
    SPLIT,

    /** Address at the top, controls at the bottom. For people who want the URL
     *  in the same place every other Android browser puts it. */
    TOP,
}

/**
 * Where the bottom bar sits horizontally.
 *
 * Centre is the obvious default, and it is also the one that covers the most
 * page. A floating "contact support" bubble, a cookie banner's accept button, or
 * a site's own bottom bar all tend to live in the middle of the lower screen,
 * because that is where a thumb reaches and where designers put the thing they
 * most want pressed. Docking the bar to one side moves it off that target.
 *
 * Left and right are not symmetric in practice: on most phones the right edge is
 * the one held by the right hand, so RIGHT is the more common choice for
 * reachability, and the settings control offers both rather than assuming.
 */
enum class ChromePosition {
    /** Docked to the leading edge, taking only the width it needs. */
    LEFT,

    /** Centred, spanning most of the width. The default. */
    CENTER,

    /** Docked to the trailing edge. */
    RIGHT,
}

/** Preferred theme for web content (prefers-color-scheme). */
enum class WebTheme {
    SYSTEM,
    DARK,
    LIGHT,
}

data class BrowserSettings(
    /** Hide the chrome when scrolling down, reveal on scroll up or tap. */
    val autoHideChrome: Boolean = true,

    /** Hide the system status/navigation bars as well as our chrome. */
    val immersiveMode: Boolean = false,

    val chromeLayout: ChromeLayout = ChromeLayout.COMPACT,

    /**
     * Horizontal placement of the bottom bar.
     *
     * Defaults to [ChromePosition.CENTER] for the same reason the pill was
     * centred to begin with: it is the least surprising, and it puts the address
     * bar under the strongest part of the thumb arc. Moving it aside is an
     * explicit choice, made to get out of a page's way.
     */
    val chromePosition: ChromePosition = ChromePosition.CENTER,

    /** Honour the wallpaper-derived Material palette as the base under the page colour. */
    val dynamicColor: Boolean = true,

    /**
     * Where a typed word goes when it is not a host.
     *
     * Defaults to [SearchEngine.KAGI]. This is the one setting that decides who
     * sees every question the user did not want to type into a search box, so
     * it is a choice rather than a hidden default, and the menu says which one
     * is in force.
     */
    val searchEngine: SearchEngine = SearchEngine.KAGI,

    /** Preferred color scheme for websites (prefers-color-scheme). */
    val webTheme: WebTheme = WebTheme.SYSTEM,

    /** Text scaling zoom percentage (e.g. 80, 100, 125, 150). */
    val textZoom: Int = 100,

    /** Revert the chrome (bottom navigation bar) from frosted glass to original Material 3 look. */
    val originalMaterial3Chrome: Boolean = false,
) {
}

/**
 * Small preference-backed settings holder.
 *
 * A [MutableStateFlow] rather than a one-shot read, so that flipping a switch
 * recomposes the theme immediately rather than on the next launch.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clear_browser", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<BrowserSettings> = _state.asStateFlow()

    fun setAutoHideChrome(enabled: Boolean) = update { it.copy(autoHideChrome = enabled) }

    fun setImmersiveMode(enabled: Boolean) = update { it.copy(immersiveMode = enabled) }

    fun setChromeLayout(layout: ChromeLayout) = update { it.copy(chromeLayout = layout) }

    fun setChromePosition(position: ChromePosition) =
        update { it.copy(chromePosition = position) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setSearchEngine(engine: SearchEngine) = update { it.copy(searchEngine = engine) }

    fun setWebTheme(theme: WebTheme) = update { it.copy(webTheme = theme) }

    fun setTextZoom(zoom: Int) = update { it.copy(textZoom = zoom) }

    fun setOriginalMaterial3Chrome(enabled: Boolean) =
        update { it.copy(originalMaterial3Chrome = enabled) }

    private inline fun update(transform: (BrowserSettings) -> BrowserSettings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean(KEY_AUTO_HIDE, next.autoHideChrome)
            .putBoolean(KEY_IMMERSIVE, next.immersiveMode)
            .putString(KEY_LAYOUT, next.chromeLayout.name)
            .putString(KEY_POSITION, next.chromePosition.name)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putString(KEY_SEARCH_ENGINE, next.searchEngine.name)
            .putString(KEY_WEB_THEME, next.webTheme.name)
            .putInt(KEY_TEXT_ZOOM, next.textZoom)
            .putBoolean(KEY_ORIGINAL_M3_CHROME, next.originalMaterial3Chrome)
            .apply()
    }

    private fun read() = BrowserSettings(
        autoHideChrome = prefs.getBoolean(KEY_AUTO_HIDE, true),
        immersiveMode = prefs.getBoolean(KEY_IMMERSIVE, false),
        chromeLayout = prefs.getString(KEY_LAYOUT, null)
            ?.let { runCatching { ChromeLayout.valueOf(it) }.getOrNull() }
            ?: ChromeLayout.COMPACT,
        chromePosition = prefs.getString(KEY_POSITION, null)
            ?.let { runCatching { ChromePosition.valueOf(it) }.getOrNull() }
            ?: ChromePosition.CENTER,
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        searchEngine = prefs.getString(KEY_SEARCH_ENGINE, null)
            ?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() }
            ?: SearchEngine.KAGI,
        webTheme = prefs.getString(KEY_WEB_THEME, null)
            ?.let { runCatching { WebTheme.valueOf(it) }.getOrNull() }
            ?: WebTheme.SYSTEM,
        textZoom = prefs.getInt(KEY_TEXT_ZOOM, 100),
        originalMaterial3Chrome = prefs.getBoolean(KEY_ORIGINAL_M3_CHROME, false),
    )

    private companion object {
        const val KEY_AUTO_HIDE = "auto_hide"
        const val KEY_IMMERSIVE = "immersive"
        const val KEY_LAYOUT = "chrome_layout"
        const val KEY_POSITION = "chrome_position"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_WEB_THEME = "web_theme"
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_ORIGINAL_M3_CHROME = "original_material3_chrome"
    }
}
