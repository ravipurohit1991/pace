package com.pace.reduction.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.domain.PaceInsights
import com.pace.reduction.domain.insightLabel
import com.pace.reduction.domain.model.BeverageType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun InsightDailyChart(report: PaceInsights, onOpenDay: (LocalDate) -> Unit) {
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val index = report.days.indexOfFirst { it.date.toString() == selectedDate }.takeIf { it >= 0 } ?: report.days.lastIndex
    val day = report.days[index]
    val locale = LocalConfiguration.current.locales[0]
    val fullDate = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val countText = if (day.recorded) pluralStringResource(R.plurals.insights_count, day.cigarettes, day.cigarettes) else stringResource(R.string.insights_unknown)
    val planText = day.ceiling?.let { stringResource(R.string.insights_plan_count, it) } ?: stringResource(R.string.insights_no_plan)
    val description = stringResource(R.string.insights_day_description, fullDate, countText, planText)
    val previous = stringResource(R.string.insights_previous_day)
    val next = stringResource(R.string.insights_next_day)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.primaryContainer
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SectionCard {
        Text(stringResource(R.string.insights_chart_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.insights_chart_hint), style = MaterialTheme.typography.bodySmall, color = muted)
        val maximum = maxOf(4, report.days.maxOf { maxOf(it.cigarettes, it.ceiling ?: 0) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(maximum.toString(), style = MaterialTheme.typography.labelSmall, color = muted)
            Text(stringResource(R.string.insights_chart_legend), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Canvas(
            Modifier.fillMaxWidth().height(156.dp)
                .pointerInput(report.days) {
                    detectTapGestures { point ->
                        val picked = (point.x / size.width * report.days.size).toInt().coerceIn(0, report.days.lastIndex)
                        selectedDate = report.days[picked].date.toString()
                    }
                }
                .semantics {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction(previous) {
                            if (index > 0) { selectedDate = report.days[index - 1].date.toString(); true } else false
                        },
                        CustomAccessibilityAction(next) {
                            if (index < report.days.lastIndex) { selectedDate = report.days[index + 1].date.toString(); true } else false
                        },
                    )
                },
        ) {
            val bottom = size.height - 8.dp.toPx()
            val top = 10.dp.toPx()
            val plotHeight = bottom - top
            val slot = size.width / report.days.size
            for (step in 0..3) {
                val y = bottom - plotHeight * step / 3
                drawLine(grid.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            report.days.forEachIndexed { position, item ->
                val x = slot * position + slot / 2
                if (position == index) drawRoundRect(secondary.copy(alpha = 0.5f), Offset(slot * position + 1, 0f),
                    Size((slot - 2).coerceAtLeast(1f), size.height), CornerRadius(6.dp.toPx()))
                val barWidth = (slot * 0.52f).coerceAtMost(26.dp.toPx())
                if (!item.recorded) {
                    drawCircle(muted.copy(alpha = 0.5f), 1.8.dp.toPx(), Offset(x, bottom))
                } else {
                    val height = (item.cigarettes.toFloat() / maximum * plotHeight).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(if (position == index) primary else primary.copy(alpha = 0.5f),
                        Offset(x - barWidth / 2, bottom - height), Size(barWidth, height), CornerRadius(4.dp.toPx()))
                }
                item.ceiling?.let { ceiling ->
                    val y = bottom - ceiling.toFloat() / maximum * plotHeight
                    drawLine(muted, Offset(slot * position + 2, y), Offset(slot * (position + 1) - 2, y),
                        1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
                }
            }
        }
        val format = DateTimeFormatter.ofPattern("d MMM", locale)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(report.days.first().date.format(format), style = MaterialTheme.typography.labelSmall, color = muted)
            Text(report.days.last().date.format(format), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedDate = report.days[index - 1].date.toString() }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, previous)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fullDate, style = MaterialTheme.typography.labelLarge)
                        Text(countText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { selectedDate = report.days[index + 1].date.toString() }, enabled = index < report.days.lastIndex) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, next)
                    }
                }
                Text(planText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DayMiniStat(day.coffee, stringResource(R.string.checkin_coffee), Modifier.weight(1f))
                    DayMiniStat(day.alcohol, stringResource(R.string.checkin_alcohol), Modifier.weight(1f))
                    DayMiniStat(day.checkIns, stringResource(R.string.insights_check_ins_label), Modifier.weight(1f))
                }
                TextButton(onClick = { onOpenDay(day.date) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.insights_open_day)) }
            }
        }
    }
}

@Composable
private fun DayMiniStat(value: Int, title: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun InsightHeatmap(report: PaceInsights, locale: Locale) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val max = report.heatmap.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
    val primary = MaterialTheme.colorScheme.primary
    val firstDay = java.time.temporal.WeekFields.of(locale).firstDayOfWeek
    val weekdays = (0..6).map { DayOfWeek.of((firstDay.value - 1 + it) % 7 + 1) }
    val periods = listOf("00–04", "04–08", "08–12", "12–16", "16–20", "20–24")
    val timeLabelWidth = 42.dp * LocalDensity.current.fontScale
    SectionCard {
        Text(stringResource(R.string.insights_heatmap_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.insights_heatmap_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(timeLabelWidth))
            weekdays.forEach { weekday ->
                Text(weekday.getDisplayName(TextStyle.NARROW, locale), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
        // Time labels grow with the user's font setting instead of breaking a clock range mid-word.
        periods.forEachIndexed { bucket, period ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(period, Modifier.width(timeLabelWidth), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                weekdays.forEach { weekday ->
                    val dayIndex = weekday.value - 1
                    val value = report.heatmap[dayIndex][bucket]
                    val cellIndex = dayIndex * 6 + bucket
                    val description = stringResource(R.string.insights_heatmap_cell, weekday.getDisplayName(TextStyle.FULL, locale), period, value)
                    val intensity = if (value == 0) 0.07f else 0.15f + 0.85f * value / max
                    Surface(
                        onClick = { selected = cellIndex }, modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = description },
                        shape = RoundedCornerShape(8.dp), color = primary.copy(alpha = intensity),
                        border = if (selected == cellIndex) androidx.compose.foundation.BorderStroke(2.dp, primary) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (value > 0) Text(value.toString(), style = MaterialTheme.typography.labelSmall,
                                color = if (intensity > 0.55f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (selected >= 0) Text(stringResource(R.string.insights_heatmap_selected,
            DayOfWeek.of(selected / 6 + 1).getDisplayName(TextStyle.FULL, locale), periods[selected % 6], report.heatmap[selected / 6][selected % 6]),
            style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.insights_heatmap_low), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            repeat(5) { Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(primary.copy(alpha = 0.1f + 0.225f * it))) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.insights_heatmap_high), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun InsightTriggers(report: PaceInsights, onCheckIn: () -> Unit) {
    SectionCard {
        Text(stringResource(R.string.insights_triggers_title), style = MaterialTheme.typography.titleLarge)
        if (report.triggers.isEmpty()) Text(stringResource(R.string.insights_trigger_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        report.triggers.take(5).forEach { trigger ->
            val known = checkInTriggers.firstOrNull { it.first == trigger.tag }?.second
            val label = known?.let { stringResource(it) } ?: trigger.tag.insightLabel()
            Text(label, style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { trigger.count.toFloat() / report.taggedCheckIns.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp))
            Text(stringResource(R.string.insights_trigger_sample, trigger.count, report.taggedCheckIns), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
        FilledTonalButton(onClick = onCheckIn) { Text(stringResource(R.string.insights_check_in)) }
    }
}

@Composable
internal fun InsightTools(report: PaceInsights, onOpenToolkit: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard {
        Text(stringResource(R.string.insights_help_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.insights_help_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (report.tools.isEmpty()) Text(stringResource(R.string.insights_help_empty), style = MaterialTheme.typography.bodyMedium)
        report.tools.take(5).forEachIndexed { index, tool ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}".padStart(2, '0'), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(tool.tool.removePrefix("MOVE:").insightLabel(), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.insights_tool_count, tool.completed), style = MaterialTheme.typography.bodySmall)
                    val drop = tool.averageDrop
                    Text(if (drop == null) stringResource(R.string.insights_tool_unrated)
                        else stringResource(if (drop >= 0) R.string.insights_tool_drop else R.string.insights_tool_rise,
                            String.format(locale, "%.1f", abs(drop)), tool.rated),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = onOpenToolkit) { Text(stringResource(R.string.insights_open_toolkit)) }
    }
}

@Composable
internal fun InsightDrinks(report: PaceInsights) {
    SectionCard {
        Text(stringResource(R.string.insights_drinks_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.insights_drinks_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        report.drinkAssociations.forEach { association ->
            val label = stringResource(if (association.type == BeverageType.COFFEE) R.string.checkin_coffee else R.string.checkin_alcohol)
            Text(stringResource(R.string.insights_drink_following, label), style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { association.followingLogs.toFloat() / association.totalLogs.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp))
            Text(stringResource(R.string.insights_drink_sample, association.followingLogs, association.totalLogs), style = MaterialTheme.typography.bodySmall)
        }
    }
}
