package dev.clearbrowser.core.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Animation specs for the browser chrome.
 *
 * ## Why these are local constants
 *
 * M3 Expressive's own motion tokens (`MotionScheme`, `MaterialExpressiveTheme`)
 * are still marked `internal` in `androidx.compose.material3:1.5.0-alpha29`, so
 * they cannot be referenced from application code yet. Rather than block on
 * that, the tokens are declared here with values chosen to match the M3
 * character: springs for anything positional, a non-overshooting tween for
 * anything perceptual.
 *
 * When 1.5.0 stabilises, the bodies of these three functions become
 * `MaterialTheme.motionScheme.defaultSpatialSpec()` and
 * `MaterialTheme.motionScheme.defaultEffectsSpec()`, and the call sites need no
 * changes. Keeping them behind functions from the start is what makes that swap
 * a two-line edit rather than a refactor.
 *
 * ## Why the distinction matters here
 *
 * The chrome's whole personality is its transitions, so the split between
 * positional and perceptual motion is not a detail. The pill sliding away
 * should feel like it has mass and settle. Its colour fading should not
 * overshoot -- an opacity that bounces reads as a glitch, and a page colour that
 * bounces is worse.
 */

/**
 * Positional motion: the chrome showing, hiding, or reshaping.
 *
 * The two spatial springs below are the same numbers written twice because
 * `spring()` is generic in its output type and Kotlin cannot widen one
 * instantiation into the other. When `MotionScheme` becomes public this pair
 * collapses into `defaultSpatialSpec()`.
 */
@Composable
fun chromeSpring(): FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 600f,
)

/** The same spring, for anything measured as a fraction rather than a position. */
fun chromeSpatialSpring(): FiniteAnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 600f,
)

/** The same spring, for slide transitions, which are measured as offsets. */
fun chromeOffsetSpring(): FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 600f,
)

/**
 * The chrome stretching between the collapsed handle and the full bar.
 *
 * Softer and slower than [chromeSpatialSpring] on purpose. This is the one
 * transition where the object changes *size*, not just position, and a stiff
 * spring makes a resizing element look like it is being yanked. A little
 * give here is what sells it as the same surface rather than two views being
 * swapped.
 *
 * Damping is kept non-bouncy for the same reason the others are: the width is
 * derived from this value, and a spring that overshoots would draw the bar
 * wider than the screen on the way past.
 */
fun chromeMorphSpring(): FiniteAnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f,
)

/**
 * Perceptual motion: opacity and colour. Deliberately a tween with no overshoot
 * so the page colour never appears to bounce between two values.
 */
fun chromeFadeSpec(): FiniteAnimationSpec<Float> = tween(durationMillis = 150)

/** Content arriving after the user summoned it, e.g. an expanded address field. */
@Composable
fun fastSpring(): FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 900f,
)

/** Linearly interpolates between two [Dp] values based on [fraction]. */
fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp =
    start + (end - start) * fraction.coerceIn(0f, 1f)

