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

private val LightColors = lightColorScheme(
    primary = Color(0xFF385B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8DC),
    onPrimaryContainer = Color(0xFF102D1D),
    secondary = Color(0xFF6C5941),
    background = Color(0xFFF5F1E8),
    surface = Color(0xFFFFFDF7),
    surfaceVariant = Color(0xFFE7E2D8),
    error = Color(0xFF8B4A43),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAECFB5),
    onPrimary = Color(0xFF173824),
    primaryContainer = Color(0xFF294C36),
    onPrimaryContainer = Color(0xFFD9E8DC),
    secondary = Color(0xFFD8C2A2),
    background = Color(0xFF171B18),
    surface = Color(0xFF1D211E),
    surfaceVariant = Color(0xFF3E463F),
    error = Color(0xFFFFB4AB),
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

