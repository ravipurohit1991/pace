package com.pace.reduction

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pace.reduction.core.designsystem.PaceTheme
import com.pace.reduction.domain.model.ThemeMode
import com.pace.reduction.feature.PaceApp

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DESTINATION = "pace_destination"
        const val DESTINATION_TOOLKIT = "toolkit"
        const val DESTINATION_COACH = "coach"
        const val DESTINATION_CALL = "call"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: PaceViewModel = viewModel(
                factory = PaceViewModel.factory(application as PaceApplication),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiState.settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle,
                )
            }
            PaceTheme(
                darkTheme = darkTheme,
                accent = uiState.settings.accentPalette,
                dynamicColor = uiState.settings.dynamicColor,
                amoledDark = uiState.settings.amoledDark,
                motionLevel = uiState.settings.motionLevel,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PaceApp(viewModel)
                }
            }
        }
    }
}
