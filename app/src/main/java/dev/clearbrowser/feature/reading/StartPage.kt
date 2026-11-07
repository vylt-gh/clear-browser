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
