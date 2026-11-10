package dev.clearbrowser.feature.reading

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R

/**
 * The browser's own new tab page.
 *
 * ## Why this is not `about:blank`
 *
 * `about:blank` is the *engine's* empty document. The engine loads one the
 * moment it is opened, before any navigation, and every browser keeps it for
 * internal use -- a popup window that only exists to carry a URL across, a
 * session that has nothing to restore, a renderer that has to be given
 * something to paint. It is not a page anyone can be shown, and it is not a
 * URL that means anything to the user.
 *
 * This browser was using it as its start page anyway, which borrowed that
 * meaning: the address bar showed `about:blank` on a fresh tab, the session
 * file recorded it, and the tab switcher drew it as a real page. Worse, the
 * blank document is *white* by default, and the engine paints it inside the
 * surface the chrome samples its blur from -- so on a cold boot the chrome
 * frosted itself with white and looked like it belonged to another app.
 *
 * `about:newtab` is the browser's own name for the page it shows when there
 * is nothing to open. It is never loaded into the engine, it is never
 * recorded in history, and it leaves `about:blank` to the engine that needs
 * it.
 */
internal const val ABOUT_NEWTAB = "about:newtab"

/**
 * The engine's empty document.
 *
 * Kept only so a session written by an older build -- which recorded the
 * engine's blank document as the tab's URL -- is still recognised as the start
 * page rather than as a real page with a broken address.
 */
internal const val ABOUT_BLANK = "about:blank"

/**
 * Whether [url] means "the browser's new tab page".
 *
 * ## Why blank counts
 *
 * A tab that has not been given a URL yet is the start page too. The engine
 * reports its blank document before the browser has anything else to say, and
 * the difference between "no page" and "the new tab page" is not one the
 * user can see.
 */
internal fun isStartPageUrl(url: String?): Boolean =
    url.isNullOrBlank() || url == ABOUT_NEWTAB || url == ABOUT_BLANK

/**
 * What a browser with nothing open should show: nothing.
 *
 * ## Why this exists at all, rather than a blank WebView
 *
 * An empty `WebView` is not the same thing as an empty screen. It is a control
 * with no content, and it looks like a page that failed to load -- there is no
 * way to tell "this is new" from "this is broken". A quiet surface that is
 * clearly deliberate is the difference between a fresh tab and a bug.
 *
 * ## Why the mark and nothing else
 *
 * A homepage is a commitment: it is the site the browser hands you when it has
 * nothing better to offer, and any link on it is a vote for where your attention
 * goes. This one votes for nowhere. The mark is drawn at low opacity because it
 * is a watermark, not a logo: it says which app you are in and then gets out of
 * the way of the address bar, which is the only thing on this screen worth
 * using.
 *
 * The image is the launcher foreground, tinted rather than shipped twice, so
 * the empty state cannot drift away from the icon it is standing in for.
 */
@Composable
fun StartPage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            // Decorative: the address bar is what this screen is for, and a
            // screen reader announcing a logo before it would only add noise.
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                MaterialTheme.colorScheme.onSurface.copy(alpha = MARK_ALPHA),
            ),
            modifier = Modifier.size(MARK_BOX),
        )
    }
}

/**
 * Faint enough to read as a watermark on both schemes.
 *
 * Ten percent is deliberate. Any stronger and it competes with the address bar
 * for the eye on the one screen where the address bar is the only thing to do;
 * any fainter and it reads as a rendering artefact.
 */
private const val MARK_ALPHA = 0.10f

/**
 * The mark's box, not the mark's size.
 *
 * The drawable is a 108dp adaptive-icon canvas with the artwork inset inside the
 * safe zone, so the visible shape is a little under half this value. Sizing the
 * box rather than the mark keeps the artwork's own proportions untouched.
 */
private val MARK_BOX = 160.dp
