package dev.clearbrowser.core.data

/**
 * Visual styling theme for the browser chrome.
 */
enum class ChromeTheme {
    /**
     * Default frosted glass styling with specular rim borders, gradient highlights,
     * and speech-bubble popup menu.
     */
    CLEAR,

    /**
     * Translucent acrylic surfaces with subtle vertical gradients and borders.
     */
    ACRYLIC,

    /**
     * Pure solid Material 3 styling from v0.1 with surfaceContainer and surfaceContainerHigh
     * without artificial borders or translucent gradients.
     */
    MATERIAL,
}
