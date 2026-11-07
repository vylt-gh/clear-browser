package dev.clearbrowser.core.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's Material theme.
 *
 * ## Why there is nothing clever in here
 *
 * This used to derive a colour scheme from the page being viewed: sample the
 * pixels, negotiate the colour into Material's lightness and chroma bounds,
 * contrast-check it, and graft the result onto a real `ColorScheme`. It was the
 * centrepiece of the project.
 *
 * It is gone because it kept costing more than it was worth. A theme is
 * recomputed on every frame the screen changes, and sampling plus gamut-fitting
 * plus a contrast search is a lot of work to do that often. More to the point, it
 * was *wrong* often enough to be distracting: a page could briefly flash a
 * colour it had not settled on, and a saturated site could push the chrome
 * somewhere the user did not ask for, with no control that was worth having.
 *
 * A browser that looks like a well-made app is not a worse browser. The page is
 * the content, and the chrome is the app, and pretending otherwise mostly
 * produced a teal toolbar for a moment on the way to a search.
 *
 * `remember` matters even now: `dynamicLightColorScheme` parses the wallpaper
 * palette on every call, and this runs on a screen that recomposes constantly.
 */
@Composable
fun ClearBrowserTheme(
    isDark: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme: ColorScheme = remember(isDark, dynamicColor, context) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (isDark) darkColorScheme() else lightColorScheme()
        }
    }

    MaterialTheme(colorScheme = scheme) {
        // The app draws directly on `surface` -- the start page, the chrome --
        // rather than inside a Material `Surface`, so nothing sets a content
        // colour for it. Compose's fallback is black, which made every untinted
        // icon (the back, reload and menu buttons) invisible in dark mode while
        // looking correct in light mode. Sheets bring their own `Surface` and
        // override this locally, as they should.
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            content()
        }
    }
}
