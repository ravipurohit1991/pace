package com.pace.reduction.feature

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.reduction.PaceUiState
import com.pace.reduction.R
import com.pace.reduction.domain.PaceInsights
import com.pace.reduction.domain.PaceInsightsCalculator
import com.pace.reduction.domain.SavingsProjection
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun rememberInsights(state: PaceUiState, range: Int): PaceInsights {
    val zone = ZoneId.systemDefault()
    val date = state.now.atZone(zone).toLocalDate()
    return remember(state.activeLogs, state.activeBeverageLogs, state.urgeSessions, state.dailySnapshots, range, date, zone) {
        PaceInsightsCalculator.calculate(state.now, zone, state.activeLogs, state.activeBeverageLogs,
            state.urgeSessions, state.dailySnapshots, range)
    }
}

@Composable
internal fun InsightsScreen(
    uiState: PaceUiState,
    onOpenSettings: () -> Unit,
    onOpenJourney: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenToolkit: () -> Unit,
    onCheckIn: () -> Unit,
    onExport: (Uri, Int) -> Unit,
) {
    var range by rememberSaveable { mutableIntStateOf(7) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var exportRange by rememberSaveable { mutableIntStateOf(7) }
    val report = rememberInsights(uiState, range)
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val listState = rememberLazyListState()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onExport(uri, exportRange)
    }
    LaunchedEffect(tab) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        PaceTopBar(
            title = stringResource(R.string.insights_title), subtitle = stringResource(R.string.insights_subtitle),
            actions = {
                IconButton(onClick = {
                    exportRange = range
                    export.launch("pace-daily-${report.days.first().date}-${report.days.last().date}.csv")
                }) { Icon(Icons.Outlined.FileDownload, stringResource(R.string.insights_export)) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title)) }
            },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            listOf(R.string.insights_trends, R.string.insights_patterns, R.string.insights_savings).forEachIndexed { index, label ->
                SegmentedButton(selected = tab == index, onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 3), icon = {}) {
                    Text(stringResource(label))
                }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("insights_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (tab != 2) {
                item(key = "range") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 30).forEach { days ->
                                FilterChip(selected = range == days, onClick = { range = days },
                                    label = { Text(stringResource(R.string.insights_range, days)) })
                            }
                        }
                        Text(stringResource(R.string.insights_complete_days,
                            report.days.first().date.format(dateFormat), report.days.last().date.format(dateFormat)),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            when (tab) {
                0 -> {
                    item(key = "hero") { InsightHero(report, locale) }
                    if (report.recordedDays == 0) item(key = "empty") {
                        InsightEmptyCard(onOpenDay = { onOpenDay(uiState.now.atZone(ZoneId.systemDefault()).toLocalDate()) })
                    }
                    item(key = "chart") { InsightDailyChart(report, onOpenDay) }
                    item(key = "stats") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                InsightStat(Icons.Outlined.CheckCircle, "${report.onPlanDays}", stringResource(R.string.insights_on_plan),
                                    stringResource(R.string.insights_plan_sample, report.plannedDays), Modifier.weight(1f))
                                InsightStat(Icons.Outlined.Schedule, report.medianGapMinutes?.let { durationText(it) } ?: "—",
                                    stringResource(R.string.insights_typical_gap), stringResource(R.string.insights_gap_sample, report.gapsMeasured), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val saved = SavingsProjection.estimate(report.avoided, 1, uiState.settings.pricePerPack, uiState.settings.cigarettesPerPack)
                                InsightStat(Icons.Outlined.Savings,
                                    if (uiState.settings.pricePerPack > 0) moneyText(saved, uiState.settings.currencyCode, locale) else "—",
                                    stringResource(R.string.insights_saved), stringResource(R.string.insights_avoided, report.avoided), Modifier.weight(1f))
                                InsightStat(Icons.Outlined.Spa, "${report.completedTools}", stringResource(R.string.insights_tools),
                                    stringResource(R.string.insights_tools_caption), Modifier.weight(1f))
                            }
                        }
                    }
                    item(key = "journey") {
                        InsightActionCard(Icons.Outlined.AutoAwesome, stringResource(R.string.insights_journey),
                            stringResource(R.string.insights_journey_body), onOpenJourney)
                    }
                    item(key = "method") {
                        Text(stringResource(R.string.insights_today_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                1 -> {
                    item(key = "heatmap") { InsightHeatmap(report, locale) }
                    item(key = "triggers") { InsightTriggers(report, onCheckIn) }
                    item(key = "tools") { InsightTools(report, onOpenToolkit) }
                    item(key = "drinks") { InsightDrinks(report) }
                }
                2 -> {
                    item(key = "savings") { SavingsLab(uiState, onOpenPlan) }
                }
            }
            item(key = "privacy") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.insights_private), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** An intentionally quiet display surface with contour lines, separate from the data chart. */
@Composable
internal fun InsightFeatureSurface(content: @Composable ColumnScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    Surface(shape = RoundedCornerShape(28.dp), color = primary, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(primary, secondary)))) {
            Canvas(Modifier.matchParentSize()) {
                repeat(5) { index ->
                    drawCircle(Color.White.copy(alpha = 0.13f), (72 + index * 22).dp.toPx(),
                        Offset(size.width + 20.dp.toPx(), 18.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun InsightHero(report: PaceInsights, locale: Locale) {
    InsightFeatureSurface {
        Text(stringResource(R.string.insights_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(report.average?.let { String.format(locale, "%.1f", it) } ?: "—",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-2).sp))
            Text(stringResource(R.string.insights_average), modifier = Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
        val change = report.changePercent
        val changeText = when {
            change == null -> stringResource(R.string.insights_change_wait)
            abs(change) < 0.5 -> stringResource(R.string.insights_change_same)
            change < 0 -> stringResource(R.string.insights_change_down, String.format(locale, "%.0f", abs(change)))
            else -> stringResource(R.string.insights_change_up, String.format(locale, "%.0f", change))
        }
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (change != null && change < 0) Icons.Outlined.SouthEast else Icons.Outlined.Insights, null, Modifier.size(18.dp))
                Text(changeText, style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(stringResource(R.string.insights_coverage, report.recordedDays, report.days.size), style = MaterialTheme.typography.labelMedium)
        if (change != null) Text(stringResource(R.string.insights_previous_coverage, report.previousRecordedDays, report.days.size), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InsightEmptyCard(onOpenDay: () -> Unit) {
    SectionCard {
        Text(stringResource(R.string.insights_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.insights_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenDay) { Text(stringResource(R.string.insights_add_history)) }
    }
}

@Composable
private fun InsightStat(icon: ImageVector, value: String, title: String, caption: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InsightActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}

internal fun moneyText(value: BigDecimal, currency: String, locale: Locale): String =
    "${java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0 }.format(value)} $currency"

@Composable
internal fun TodayReflectionCard(uiState: PaceUiState, onCheckIn: () -> Unit, onJournal: () -> Unit) {
    val today = uiState.now.atZone(ZoneId.systemDefault()).toLocalDate()
    val count = uiState.urgeSessions.count { it.tool == "CHECK_IN" && it.startedAt.atZone(ZoneId.systemDefault()).toLocalDate() == today }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Spa, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.pulse_eyebrow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        }
        Text(stringResource(R.string.pulse_title), style = MaterialTheme.typography.titleLarge)
        Text(if (count > 0) pluralStringResource(R.plurals.pulse_reflected, count, count) else stringResource(R.string.pulse_body),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = onCheckIn) { Text(stringResource(R.string.insights_check_in)) }
            TextButton(onClick = onJournal) { Text(stringResource(R.string.pulse_journal)); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
        }
    }
}
