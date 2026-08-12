package com.pace.reduction.core.designsystem

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pace.reduction.domain.model.MotionLevel

/**
 * How much this build of the interface is allowed to move.
 *
 * Every animation in the app asks this object for its timing rather than hard-coding one, so the
 * motion setting — and the system-wide "remove animations" accessibility switch — genuinely turn
 * everything off instead of most things.
 */
@Immutable
data class PaceMotion(val level: MotionLevel = MotionLevel.FULL) {
    val enabled: Boolean get() = level != MotionLevel.NONE

    /** Decoration that exists only to delight: gradients drifting, rings breathing, confetti. */
    val flourishes: Boolean get() = level == MotionLevel.FULL

    /** Scales a duration, collapsing to an instant cut when motion is off. */
    fun duration(millis: Int): Int = when (level) {
        MotionLevel.FULL -> millis
        MotionLevel.SUBTLE -> (millis * 0.65f).toInt()
        MotionLevel.NONE -> 0
    }

    fun <T> eased(millis: Int = 320): FiniteAnimationSpec<T> =
        if (enabled) tween(durationMillis = duration(millis), easing = FastOutSlowInEasing) else snap()

    /** A spring for anything the user's own touch set in motion. */
    fun <T> springy(): FiniteAnimationSpec<T> = when (level) {
        MotionLevel.FULL -> spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
        MotionLevel.SUBTLE -> spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
        MotionLevel.NONE -> snap()
    }
}

val LocalMotion = staticCompositionLocalOf { PaceMotion() }

/**
 * Moving between screens that are peers rather than parent and child — the four tabs.
 *
 * The leaving screen is fully gone before the arriving one begins, and that stagger is the whole
 * point: two dense layouts crossfading through each other reads as a double exposure, with one
 * screen's headings legible through the other's, rather than as motion. Nothing slides, because
 * sliding claims a hierarchy the bottom bar does not have — the tabs sit beside each other.
 */
fun PaceMotion.fadeThrough(): ContentTransform {
    val leaving = duration(90)
    val arriving = duration(210)
    return (
        fadeIn(tween(arriving, delayMillis = leaving, easing = FastOutSlowInEasing)) +
            scaleIn(
                tween(arriving, delayMillis = leaving, easing = FastOutSlowInEasing),
                initialScale = 0.96f,
            )
        ) togetherWith fadeOut(tween(leaving))
}

/**
 * Moving between a screen and one stacked on top of it: the plan, settings, history, a call.
 *
 * Both screens travel the same way at once, so the pair reads as one sheet of paper moving under
 * the eye. [forward] false plays the identical motion in reverse, which is what makes a back press
 * feel like an undo of the tap that opened the screen rather than a second, unrelated journey.
 */
fun PaceMotion.slideAlong(forward: Boolean): ContentTransform {
    val leaving = duration(90)
    val arriving = duration(220)
    val travel = duration(320)
    val towards = if (forward) 1 else -1
    return (
        slideInHorizontally(tween(travel, easing = FastOutSlowInEasing)) { width ->
            towards * width / 6
        } + fadeIn(tween(arriving, delayMillis = leaving, easing = FastOutSlowInEasing))
        ) togetherWith (
        slideOutHorizontally(tween(travel, easing = FastOutSlowInEasing)) { width ->
            -towards * width / 6
        } + fadeOut(tween(leaving))
        )
}

/**
 * Combines the app's own setting with the platform's animator scale, so a user who has turned
 * animations off system-wide does not have to find the setting here as well.
 */
@Composable
fun rememberPaceMotion(level: MotionLevel): PaceMotion {
    val resolver = LocalContext.current.contentResolver
    val systemScale = remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
    return remember(level, systemScale) {
        PaceMotion(if (systemScale == 0f) MotionLevel.NONE else level)
    }
}

/** Softer than Material's defaults — this app is meant to feel unhurried. */
val PaceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * Shrinks slightly while held. Buttons that answer the finger immediately feel responsive even
 * when the work behind them is not, which matters most on the log button.
 */
fun Modifier.pressScale(interactionSource: InteractionSource, pressed: Float = 0.96f): Modifier =
    composed {
        val motion = LocalMotion.current
        val isPressed by interactionSource.collectIsPressedAsState()
        val factor by animateFloatAsState(
            targetValue = if (isPressed && motion.enabled) pressed else 1f,
            animationSpec = motion.springy(),
            label = "pressScale",
        )
        scale(factor)
    }

/**
 * A slow swell, for things that are waiting rather than working: the ring while a window is open,
 * the pause timer, the widget prompt.
 */
fun Modifier.breathe(
    minScale: Float = 0.97f,
    maxScale: Float = 1.03f,
    periodMillis: Int = 2_600,
): Modifier = composed {
    val motion = LocalMotion.current
    if (!motion.flourishes) return@composed this
    val transition = rememberInfiniteTransition(label = "breathe")
    val factor by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.duration(periodMillis), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breatheScale",
    )
    scale(factor)
}

/** The same swell applied to opacity, for glows that should not change anything's size. */
fun Modifier.pulseAlpha(min: Float = 0.45f, max: Float = 1f, periodMillis: Int = 2_200): Modifier =
    composed {
        val motion = LocalMotion.current
        if (!motion.flourishes) return@composed this
        val transition = rememberInfiniteTransition(label = "pulse")
        val value by transition.animateFloat(
            initialValue = min,
            targetValue = max,
            animationSpec = infiniteRepeatable(
                animation = tween(motion.duration(periodMillis)),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        alpha(value)
    }

/**
 * Fades and lifts content into place the first time it is composed, offset by [index] so a list
 * arrives as a cascade rather than a slab.
 *
 * "First time" is saved rather than remembered, because a lazy list disposes rows that scroll away
 * and an arrival animation that replays on every scroll back is a tic, not a flourish.
 */
fun Modifier.entrance(index: Int = 0, distance: Float = 18f): Modifier = composed {
    val motion = LocalMotion.current
    if (!motion.enabled) return@composed this
    var appeared by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.duration(360),
            delayMillis = motion.duration((index * 45).coerceAtMost(270)),
        ),
        label = "entrance",
    )
    LaunchedEffect(Unit) { appeared = true }
    graphicsLayer {
        alpha = progress
        translationY = distance * (1f - progress)
    }
}

/**
 * A number that rolls when it changes. Used for counts that the user just caused to change, where
 * the movement is the receipt for their tap.
 */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            if (!motion.enabled) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                val rising = targetState > initialState
                val slide = tween<androidx.compose.ui.unit.IntOffset>(motion.duration(340))
                val fade = tween<Float>(motion.duration(340))
                (
                    slideInVertically(slide) { height -> if (rising) height else -height } +
                        fadeIn(fade)
                    ) togetherWith (
                    slideOutVertically(slide) { height -> if (rising) -height else height } +
                        fadeOut(fade)
                    )
            }
        },
        label = "animatedCount",
    ) { target ->
        Text(text = target.toString(), style = style)
    }
}
