package com.pace.reduction.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pace.reduction.domain.model.AccentPalette
import com.pace.reduction.domain.model.MotionLevel

/**
 * A calm, low-chroma system. Every container/on-container pair is set explicitly rather than left
 * to Material's derivation, which previously produced a pink tertiary that clashed with the greens
 * and low-contrast text on the accent surfaces.
 *
 * The neutrals are shared by every accent; only the tinted roles change, which is what keeps five
 * palettes feeling like one app rather than five skins.
 */
private fun lightSchemeFor(accent: AccentPalette): ColorScheme {
    val spec = accent.spec
    return lightColorScheme(
        primary = spec.lightPrimary,
        onPrimary = contrastingInk(spec.lightPrimary),
        primaryContainer = spec.lightPrimaryContainer,
        onPrimaryContainer = spec.lightPrimary.deepened(),
        secondary = spec.lightPrimary.deepened(0.15f),
        onSecondary = Color.White,
        secondaryContainer = spec.lightSecondaryContainer,
        onSecondaryContainer = spec.lightPrimary.deepened(0.7f),
        tertiary = spec.lightTertiary,
        onTertiary = contrastingInk(spec.lightTertiary),
        tertiaryContainer = spec.lightTertiary.lightened(0.82f),
        onTertiaryContainer = spec.lightTertiary.deepened(),
        background = Color(0xFFF7F4EE),
        onBackground = Color(0xFF1A1D1A),
        surface = Color(0xFFFFFCF6),
        onSurface = Color(0xFF1A1D1A),
        surfaceVariant = Color(0xFFE7E6DF),
        onSurfaceVariant = Color(0xFF464A45),
        // The whole container ramp has to be stated. Material only derives the roles a scheme
        // leaves out from its own baseline, which is violet — and `surfaceContainer` is what the
        // navigation bar paints itself with, so leaving it out put a lavender bar under every
        // screen of an otherwise warm, low-chroma app.
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFBF8F2),
        surfaceContainer = Color(0xFFF3F0E8),
        surfaceContainerHigh = Color(0xFFEEEBE3),
        surfaceContainerHighest = Color(0xFFEBE9E1),
        surfaceBright = Color(0xFFFFFCF6),
        surfaceDim = Color(0xFFDEDCD4),
        inverseSurface = Color(0xFF2F322E),
        inverseOnSurface = Color(0xFFF0F1EB),
        scrim = Color(0xFF000000),
        outline = Color(0xFF767B74),
        outlineVariant = Color(0xFFC6C9C1),
        error = Color(0xFF8F4A46),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF7DAD7),
        onErrorContainer = Color(0xFF3B0B09),
    )
}

private fun darkSchemeFor(accent: AccentPalette, amoled: Boolean): ColorScheme {
    val spec = accent.spec
    // AMOLED trades the tinted neutrals for true black, which only pays off if the raised
    // surfaces stay distinguishable — hence near-black rather than black for surface.
    val background = if (amoled) Color(0xFF000000) else Color(0xFF11140F)
    val surface = if (amoled) Color(0xFF0A0B09) else Color(0xFF181C17)
    return darkColorScheme(
        primary = spec.darkPrimary,
        onPrimary = contrastingInk(spec.darkPrimary),
        primaryContainer = spec.darkPrimaryContainer,
        onPrimaryContainer = spec.darkPrimary.lightened(0.25f),
        secondary = spec.darkPrimary.lightened(0.1f),
        onSecondary = spec.darkPrimaryContainer.deepened(0.4f),
        secondaryContainer = spec.darkSecondaryContainer,
        onSecondaryContainer = spec.darkPrimary.lightened(0.3f),
        tertiary = spec.darkTertiary,
        onTertiary = contrastingInk(spec.darkTertiary),
        tertiaryContainer = spec.darkTertiary.deepened(0.72f),
        onTertiaryContainer = spec.darkTertiary.lightened(0.2f),
        background = background,
        onBackground = Color(0xFFE2E4DE),
        surface = surface,
        onSurface = Color(0xFFE2E4DE),
        surfaceVariant = if (amoled) Color(0xFF1C1E1B) else Color(0xFF32372F),
        onSurfaceVariant = Color(0xFFC2C7BE),
        // Same reason as the light scheme: unstated container roles fall back to violet.
        surfaceContainerLowest = if (amoled) Color(0xFF000000) else Color(0xFF0C0F0B),
        surfaceContainerLow = if (amoled) Color(0xFF0A0B09) else Color(0xFF151913),
        surfaceContainer = if (amoled) Color(0xFF0E100D) else Color(0xFF1A1E18),
        surfaceContainerHigh = if (amoled) Color(0xFF121410) else Color(0xFF1E231C),
        surfaceContainerHighest = if (amoled) Color(0xFF151714) else Color(0xFF23271F),
        surfaceBright = if (amoled) Color(0xFF2A2C28) else Color(0xFF373B34),
        surfaceDim = if (amoled) Color(0xFF000000) else Color(0xFF11140F),
        inverseSurface = Color(0xFFE2E4DE),
        inverseOnSurface = Color(0xFF1A1D1A),
        scrim = Color(0xFF000000),
        outline = Color(0xFF8C918A),
        outlineVariant = if (amoled) Color(0xFF2A2D28) else Color(0xFF434841),
        error = Color(0xFFF2B5B0),
        onError = Color(0xFF551A16),
        errorContainer = Color(0xFF72332E),
        onErrorContainer = Color(0xFFF7DAD7),
    )
}

@Composable
fun PaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentPalette = AccentPalette.SAGE,
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    motionLevel: MotionLevel = MotionLevel.FULL,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = remember(darkTheme, accent, supportsDynamic, amoledDark) {
        when {
            supportsDynamic && darkTheme -> dynamicDarkColorScheme(context).let {
                if (amoledDark) it.copy(background = Color.Black, surface = Color(0xFF0A0A0A)) else it
            }
            supportsDynamic -> dynamicLightColorScheme(context)
            darkTheme -> darkSchemeFor(accent, amoledDark)
            else -> lightSchemeFor(accent)
        }
    }

    CompositionLocalProvider(LocalMotion provides rememberPaceMotion(motionLevel)) {
        MaterialTheme(colorScheme = colors, shapes = PaceShapes, content = content)
    }
}
