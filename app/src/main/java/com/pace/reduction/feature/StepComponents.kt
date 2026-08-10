package com.pace.reduction.feature

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.domain.StepDay
import com.pace.reduction.domain.StepMetrics
import java.time.format.TextStyle
import java.util.Locale

/**
 * Walking, on the Progress screen.
 *
 * Steps are here rather than beside the cigarette count because they are not a target the app sets
 * — nothing in the plan asks for a number of steps, and a goal ring would invent one. They are
 * offered as evidence of what the day contained instead, which is the same job the rest of this
 * screen does.
 */
@Composable
internal fun StepSection(
    steps: StepMetrics,
    rangeDays: Int,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onToggle(true) }

    SectionCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        when {
                            !steps.available -> R.string.steps_unavailable
                            !steps.enabled -> R.string.steps_body
                            else -> R.string.steps_estimate_note
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (steps.available) {
                Switch(
                    checked = steps.enabled,
                    onCheckedChange = { wanted ->
                        // The permission only exists from Android 10; below that the toggle is the
                        // only gate there is.
                        if (wanted && !steps.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            onToggle(wanted)
                        }
                    },
                )
            }
        }

        if (!steps.available || !steps.enabled) return@SectionCard

        if (!steps.permissionGranted) {
            Text(
                stringResource(R.string.steps_permission_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.DirectionsWalk, contentDescription = null)
                Text(stringResource(R.string.steps_permission_action))
            }
            return@SectionCard
        }

        StatGrid(
            listOf(
                formatSteps(steps.todaySteps, locale) to stringResource(R.string.steps_today),
                formatKm(steps.todayDistanceKm, locale) to stringResource(R.string.steps_distance_today),
                formatSteps(steps.sevenDayAverage.toLong(), locale) to stringResource(R.string.steps_avg_seven),
                formatSteps(steps.thirtyDayAverage.toLong(), locale) to stringResource(R.string.steps_avg_thirty),
            ),
        )

        if (!steps.hasData) {
            Text(
                stringResource(R.string.steps_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        val visible = steps.days.takeLast(rangeDays).filter { it.steps > 0 }
        val peak = visible.maxOfOrNull { it.steps }?.coerceAtLeast(1L) ?: 1L
        visible.forEach { day ->
            StepDayBar(day = day, peak = peak, locale = locale)
        }

        steps.bestDay?.let { best ->
            Text(
                stringResource(
                    R.string.steps_best_day,
                    formatSteps(best.steps, locale),
                    formatKm(best.distanceKm, locale),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(
                R.string.steps_total,
                formatSteps(steps.totalSteps, locale),
                formatKm(steps.totalDistanceKm, locale),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepDayBar(day: StepDay, peak: Long, locale: Locale) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                stringResource(
                    R.string.steps_day_value,
                    formatSteps(day.steps, locale),
                    formatKm(day.distanceKm, locale),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { (day.steps.toFloat() / peak.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/** Grouped, because five-figure step counts are hard to read as a bare run of digits. */
internal fun formatSteps(steps: Long, locale: Locale): String =
    String.format(locale, "%,d", steps.coerceAtLeast(0))

internal fun formatKm(km: Double, locale: Locale): String =
    String.format(locale, if (km < 10) "%.2f km" else "%.1f km", km.coerceAtLeast(0.0))

/** Compact form for the Today stat tile, where there is no room for a thousands separator. */
internal fun formatStepsCompact(steps: Long, locale: Locale): String = when {
    steps < 1_000 -> steps.coerceAtLeast(0).toString()
    else -> String.format(locale, "%.1fk", steps / 1_000.0)
}
