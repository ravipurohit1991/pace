package com.pace.reduction.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.domain.HabitMetric
import com.pace.reduction.domain.HabitTrend
import com.pace.reduction.domain.model.PlanSettings
import java.time.format.DateTimeFormatter

/** An interactive eight-week view that keeps unlike units separate and comparable. */
@Composable
internal fun HabitTrendCard(
    trend: HabitTrend,
    settings: PlanSettings,
    onOpenLedger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = buildList {
        add(HabitMetric.CIGARETTES)
        if (settings.coffeeTrackingEnabled || trend.total(HabitMetric.COFFEE) > 0) add(HabitMetric.COFFEE)
        if (settings.alcoholTrackingEnabled || trend.total(HabitMetric.ALCOHOL) > 0) add(HabitMetric.ALCOHOL)
        if (settings.otherBeverageTrackingEnabled || trend.total(HabitMetric.OTHER) > 0) add(HabitMetric.OTHER)
        add(HabitMetric.CHECK_INS)
        if (settings.stepCountingEnabled || trend.total(HabitMetric.STEPS) > 0) add(HabitMetric.STEPS)
    }
    var metric by rememberSaveable { mutableStateOf(HabitMetric.CIGARETTES) }
    LaunchedEffect(available) {
        if (metric !in available) metric = HabitMetric.CIGARETTES
    }
    val values = trend.values(metric)
    val label = metricLabel(metric, settings.otherBeverageLabel)

    SectionCard(modifier) {
        Text(stringResource(R.string.trend_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.trend_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            available.forEach { option ->
                FilterChip(
                    selected = metric == option,
                    onClick = { metric = option },
                    label = { Text(metricLabel(option, settings.otherBeverageLabel)) },
                    leadingIcon = {
                        Icon(metricIcon(option), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
        StatGrid(
            listOf(
                trend.total(metric).toString() to stringResource(R.string.trend_total),
                String.format("%.1f", trend.average(metric)) to stringResource(R.string.trend_weekly_average),
                (values.lastOrNull() ?: 0).toString() to stringResource(R.string.trend_this_week),
            ),
        )
        TrendBars(
            values = values,
            labels = trend.weeks.map { it.start.format(DateTimeFormatter.ofPattern("M/d")) },
            colour = metricBarColour(metric),
            description = stringResource(R.string.trend_accessible, label, values.joinToString(", ")),
        )
        OutlinedButton(onClick = onOpenLedger, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.ledger_open_action))
        }
    }
}

@Composable
private fun TrendBars(
    values: List<Int>,
    labels: List<String>,
    colour: Color,
    description: String,
) {
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 8.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val chartHeight = size.height - 8.dp.toPx()
        drawLine(
            color = baseline,
            start = Offset(0f, chartHeight),
            end = Offset(size.width, chartHeight),
            strokeWidth = 1.dp.toPx(),
        )
        values.forEachIndexed { index, value ->
            val height = if (value == 0) 3.dp.toPx() else chartHeight * (value.toFloat() / max)
            drawRoundRect(
                color = colour.copy(alpha = if (value == 0) 0.28f else 0.9f),
                topLeft = Offset(index * (barWidth + gap), chartHeight - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        values.forEach { value ->
            Text(
                value.toString(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun metricBarColour(metric: HabitMetric): Color = when (metric) {
    HabitMetric.CIGARETTES -> MaterialTheme.colorScheme.primary
    HabitMetric.COFFEE -> MaterialTheme.colorScheme.secondary
    HabitMetric.ALCOHOL -> MaterialTheme.colorScheme.tertiary
    HabitMetric.OTHER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    HabitMetric.CHECK_INS -> MaterialTheme.colorScheme.secondary
    HabitMetric.STEPS -> MaterialTheme.colorScheme.primary
}
