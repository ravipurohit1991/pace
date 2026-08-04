package com.pace.reduction.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pace.reduction.domain.model.AccentPalette

/**
 * One accent family, expressed as the handful of hues a scheme actually needs. Every remaining
 * Material role is derived, so a new palette is six colours rather than thirty, and the
 * on-colours can never drift out of contrast with what they sit on.
 *
 * The widget draws from the same source: an accent that only existed inside Compose would leave
 * the home screen looking like a different app.
 */
internal data class AccentSpec(
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightSecondaryContainer: Color,
    val lightTertiary: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkSecondaryContainer: Color,
    val darkTertiary: Color,
    /** Widget backdrop, top-to-bottom, for a light home screen. */
    val widgetLight: Pair<Color, Color>,
    /** Widget backdrop for a dark home screen. */
    val widgetDark: Pair<Color, Color>,
)

internal val AccentPalette.spec: AccentSpec
    get() = when (this) {
        AccentPalette.SAGE -> AccentSpec(
            lightPrimary = Color(0xFF3A6B4E),
            lightPrimaryContainer = Color(0xFFC9E3D1),
            lightSecondaryContainer = Color(0xFFD7E6DC),
            lightTertiary = Color(0xFF7A6244),
            darkPrimary = Color(0xFFA6CDB2),
            darkPrimaryContainer = Color(0xFF27503A),
            darkSecondaryContainer = Color(0xFF374C40),
            darkTertiary = Color(0xFFDCC3A0),
            widgetLight = Color(0xFF4C7A5C) to Color(0xFF2F5740),
            widgetDark = Color(0xFF24422F) to Color(0xFF14261B),
        )
        AccentPalette.OCEAN -> AccentSpec(
            lightPrimary = Color(0xFF2C6076),
            lightPrimaryContainer = Color(0xFFC3E1EE),
            lightSecondaryContainer = Color(0xFFD3E4EC),
            lightTertiary = Color(0xFF4C5B96),
            darkPrimary = Color(0xFF9BCBDE),
            darkPrimaryContainer = Color(0xFF1E4859),
            darkSecondaryContainer = Color(0xFF33474F),
            darkTertiary = Color(0xFFB6BDE9),
            widgetLight = Color(0xFF39718A) to Color(0xFF204C5F),
            widgetDark = Color(0xFF1B3D4C) to Color(0xFF0E222B),
        )
        AccentPalette.EMBER -> AccentSpec(
            lightPrimary = Color(0xFF9A4A2C),
            lightPrimaryContainer = Color(0xFFF6D5C5),
            lightSecondaryContainer = Color(0xFFF0DED2),
            lightTertiary = Color(0xFF7C6023),
            darkPrimary = Color(0xFFF0B49A),
            darkPrimaryContainer = Color(0xFF74351A),
            darkSecondaryContainer = Color(0xFF54413A),
            darkTertiary = Color(0xFFE3C784),
            widgetLight = Color(0xFFB05A38) to Color(0xFF833C22),
            widgetDark = Color(0xFF6B331B) to Color(0xFF3B1B0D),
        )
        AccentPalette.VIOLET -> AccentSpec(
            lightPrimary = Color(0xFF5E4A8C),
            lightPrimaryContainer = Color(0xFFDCD2F2),
            lightSecondaryContainer = Color(0xFFE0DAEC),
            lightTertiary = Color(0xFF8B4A73),
            darkPrimary = Color(0xFFC3B4E8),
            darkPrimaryContainer = Color(0xFF463572),
            darkSecondaryContainer = Color(0xFF453D57),
            darkTertiary = Color(0xFFEBB2D4),
            widgetLight = Color(0xFF6E58A2) to Color(0xFF4B3B76),
            widgetDark = Color(0xFF3D2F65) to Color(0xFF221A3A),
        )
        AccentPalette.SLATE -> AccentSpec(
            lightPrimary = Color(0xFF44525C),
            lightPrimaryContainer = Color(0xFFD3DCE3),
            lightSecondaryContainer = Color(0xFFDDE1E4),
            lightTertiary = Color(0xFF5C5350),
            darkPrimary = Color(0xFFB6C4CE),
            darkPrimaryContainer = Color(0xFF35424B),
            darkSecondaryContainer = Color(0xFF41474B),
            darkTertiary = Color(0xFFD3C7C2),
            widgetLight = Color(0xFF56656F) to Color(0xFF3A464F),
            widgetDark = Color(0xFF2D383F) to Color(0xFF171E22),
        )
    }

/** The widget's fill for a light and a dark home screen, already carrying the chosen translucency. */
data class WidgetBackdrop(val day: Color, val night: Color)

/**
 * The accent fill behind the widget.
 *
 * Glass is not a separate colour set — it is the same accent let through at a lower alpha so the
 * wallpaper reads underneath. Keeping it as one dimension rather than a parallel palette is what
 * stops five accents times three styles from becoming fifteen things to maintain.
 */
fun AccentPalette.widgetBackdrop(opacityPercent: Int, glass: Boolean): WidgetBackdrop {
    val spec = spec
    val alpha = (opacityPercent.coerceIn(35, 100) / 100f) * (if (glass) 0.72f else 1f)
    return WidgetBackdrop(
        day = spec.widgetLight.first.copy(alpha = alpha),
        night = spec.widgetDark.first.copy(alpha = alpha),
    )
}

/**
 * Black or white, whichever stays readable on [background].
 *
 * The threshold sits above the usual 0.5 because Material's mid-tone containers land just either
 * side of it, and light text on a mid green reads worse than dark text does.
 */
internal fun contrastingInk(background: Color): Color =
    if (background.luminance() > 0.42f) Color(0xFF0E1410) else Color(0xFFFFFFFF)

/** A deeper, more saturated relative of [this], used for text sitting on a pale container. */
internal fun Color.deepened(amount: Float = 0.62f): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)

/** A pale relative of [this], used for text sitting on a dark container. */
internal fun Color.lightened(amount: Float = 0.7f): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)
