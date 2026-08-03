package com.pace.reduction.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * A calm sage-and-clay palette. Every container/on-container pair is set explicitly rather than
 * left to Material's derivation, which previously produced the pink tertiary that clashed with
 * the greens, and low-contrast text on the accent surfaces.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF3A6B4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9E3D1),
    onPrimaryContainer = Color(0xFF0C2417),
    secondary = Color(0xFF4F6B5C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E6DC),
    onSecondaryContainer = Color(0xFF16281E),
    tertiary = Color(0xFF7A6244),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0E2CD),
    onTertiaryContainer = Color(0xFF2A1D0B),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF1A1D1A),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1A1D1A),
    surfaceVariant = Color(0xFFE4E5DE),
    onSurfaceVariant = Color(0xFF464A45),
    outline = Color(0xFF767B74),
    outlineVariant = Color(0xFFC6C9C1),
    error = Color(0xFF8F4A46),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF7DAD7),
    onErrorContainer = Color(0xFF3B0B09),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6CDB2),
    onPrimary = Color(0xFF10301F),
    primaryContainer = Color(0xFF27503A),
    onPrimaryContainer = Color(0xFFC9E3D1),
    secondary = Color(0xFFB6CBBD),
    onSecondary = Color(0xFF21372B),
    secondaryContainer = Color(0xFF374C40),
    onSecondaryContainer = Color(0xFFD7E6DC),
    tertiary = Color(0xFFDCC3A0),
    onTertiary = Color(0xFF3D2E17),
    tertiaryContainer = Color(0xFF56442B),
    onTertiaryContainer = Color(0xFFF0E2CD),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE2E4DE),
    surface = Color(0xFF181C17),
    onSurface = Color(0xFFE2E4DE),
    surfaceVariant = Color(0xFF3A3F39),
    onSurfaceVariant = Color(0xFFC2C7BE),
    outline = Color(0xFF8C918A),
    outlineVariant = Color(0xFF434841),
    error = Color(0xFFF2B5B0),
    onError = Color(0xFF551A16),
    errorContainer = Color(0xFF72332E),
    onErrorContainer = Color(0xFFF7DAD7),
)

@Composable
fun PaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(LocalContext.current)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(LocalContext.current)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}
