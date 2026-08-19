package com.pace.reduction.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.SmokingRooms
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.domain.HabitMetric
import com.pace.reduction.domain.model.BeverageLog
import com.pace.reduction.domain.model.BeverageType
import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.UrgeSession
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class LedgerFilter { ALL, CIGARETTES, DRINKS, CHECK_INS }

private data class LedgerEntry(
    val id: String,
    val metric: HabitMetric,
    val occurredAt: Instant,
    val source: String,
    val reversed: Boolean,
    val editable: Boolean = true,
)

/** A single, auditable timeline for every manually logged habit event. */
@Composable
internal fun HistoryEditorScreen(
    uiState: PaceUiState,
    viewModel: PaceViewModel,
    onBack: () -> Unit,
) {
    val date by viewModel.editorDate.collectAsStateWithLifecycle()
    val cigarettes by viewModel.editorLogs.collectAsStateWithLifecycle()
    val beverages by viewModel.editorBeverageLogs.collectAsStateWithLifecycle()
    var hour by rememberSaveable { mutableStateOf(LocalTime.now().hour.toString().padStart(2, '0')) }
    var minute by rememberSaveable { mutableStateOf(LocalTime.now().minute.toString().padStart(2, '0')) }
    var metric by rememberSaveable { mutableStateOf(HabitMetric.CIGARETTES) }
    var filter by rememberSaveable { mutableStateOf(LedgerFilter.ALL) }

    LaunchedEffect(Unit) { viewModel.refreshEditorLogs() }

    val today = LocalDate.now()
    val parsedHour = hour.toIntOrNull()
    val parsedMinute = minute.toIntOrNull()
    val timeValid = parsedHour in 0..23 && parsedMinute in 0..59
    val zone = ZoneId.systemDefault()
    val locale = LocalLocale.current.platformLocale
    val sessions = uiState.urgeSessions.filter { it.startedAt.atZone(zone).toLocalDate() == date }
    val steps = uiState.steps.days.firstOrNull { it.date == date }?.steps ?: 0L
    val entries = ledgerEntries(cigarettes, beverages, sessions)
    val visibleEntries = entries.filter { entry ->
        when (filter) {
            LedgerFilter.ALL -> true
            LedgerFilter.CIGARETTES -> entry.metric == HabitMetric.CIGARETTES
            LedgerFilter.DRINKS -> entry.metric != HabitMetric.CIGARETTES
                && entry.metric != HabitMetric.CHECK_INS
            LedgerFilter.CHECK_INS -> entry.metric == HabitMetric.CHECK_INS
        }
    }
    val activeEntries = entries.filterNot(LedgerEntry::reversed)
    val enabledMetrics = buildList {
        add(HabitMetric.CIGARETTES)
        if (uiState.settings.coffeeTrackingEnabled) add(HabitMetric.COFFEE)
        if (uiState.settings.alcoholTrackingEnabled) add(HabitMetric.ALCOHOL)
        if (uiState.settings.otherBeverageTrackingEnabled) add(HabitMetric.OTHER)
    }

    PaceScreen(
        title = stringResource(R.string.ledger_title),
        subtitle = stringResource(R.string.ledger_subtitle),
        onBack = onBack,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.selectEditorDate(date.minusDays(1)) }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.history_previous_day))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.ledger_event_count,
                                activeEntries.size,
                                activeEntries.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.selectEditorDate(date.plusDays(1)) },
                        enabled = date.isBefore(today),
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.history_next_day))
                    }
                }
                StatGrid(
                    listOf(
                        activeEntries.count { it.metric == HabitMetric.CIGARETTES }.toString() to
                            stringResource(R.string.ledger_cigarettes),
                        activeEntries.count {
                            it.metric in setOf(HabitMetric.COFFEE, HabitMetric.ALCOHOL, HabitMetric.OTHER)
                        }.toString() to stringResource(R.string.ledger_drinks),
                        activeEntries.count { it.metric == HabitMetric.CHECK_INS }.toString() to
                            stringResource(R.string.ledger_check_ins),
                        formatStepsCompact(steps, locale) to
                            stringResource(R.string.ledger_steps),
                    ),
                )
            }
        }

        item {
            SectionCard {
                Text(stringResource(R.string.ledger_add_title), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    enabledMetrics.forEach { option ->
                        FilterChip(
                            selected = metric == option,
                            onClick = { metric = option },
                            label = { Text(metricLabel(option, uiState.settings.otherBeverageLabel)) },
                            leadingIcon = {
                                Icon(metricIcon(option), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) hour = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.history_hour)) },
                        isError = hour.isNotEmpty() && parsedHour !in 0..23,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) minute = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.history_minute)) },
                        isError = minute.isNotEmpty() && parsedMinute !in 0..59,
                        singleLine = true,
                    )
                }
                TextButton(
                    onClick = {
                        val now = LocalTime.now()
                        hour = now.hour.toString().padStart(2, '0')
                        minute = now.minute.toString().padStart(2, '0')
                    },
                    enabled = date == today,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.ledger_use_now)) }
                Button(
                    onClick = { viewModel.addLedgerEntry(metric, parsedHour ?: 0, parsedMinute ?: 0) },
                    enabled = timeValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            R.string.ledger_add_action,
                            metricLabel(metric, uiState.settings.otherBeverageLabel),
                        ),
                    )
                }
                Text(
                    stringResource(R.string.ledger_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ledger_timeline), style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    LedgerFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(ledgerFilterLabel(option)) },
                        )
                    }
                }
            }
        }

        if (visibleEntries.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.ledger_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                SectionCard {
                    visibleEntries.forEachIndexed { index, entry ->
                        LedgerRow(
                            entry = entry,
                            otherLabel = uiState.settings.otherBeverageLabel,
                            onDelete = { viewModel.deleteLedgerEntry(entry.metric, entry.id) },
                        )
                        if (index != visibleEntries.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun ledgerEntries(
    cigarettes: List<CigaretteLog>,
    beverages: List<BeverageLog>,
    sessions: List<UrgeSession>,
): List<LedgerEntry> = buildList {
    cigarettes.forEach {
        add(LedgerEntry(it.id, HabitMetric.CIGARETTES, it.occurredAt, it.source, it.reversedAt != null))
    }
    beverages.forEach {
        val metric = when (it.type) {
            BeverageType.COFFEE -> HabitMetric.COFFEE
            BeverageType.ALCOHOL -> HabitMetric.ALCOHOL
            BeverageType.OTHER -> HabitMetric.OTHER
        }
        add(LedgerEntry(it.id, metric, it.occurredAt, it.source, it.reversedAt != null))
    }
    sessions.forEach {
        add(
            LedgerEntry(
                id = it.id,
                metric = HabitMetric.CHECK_INS,
                occurredAt = it.startedAt,
                source = it.tool,
                reversed = false,
                editable = false,
            ),
        )
    }
}.sortedByDescending(LedgerEntry::occurredAt)

@Composable
private fun LedgerRow(entry: LedgerEntry, otherLabel: String, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(metricContainer(entry.metric), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                metricIcon(entry.metric),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = metricContent(entry.metric),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                metricLabel(entry.metric, otherLabel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.reversed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                stringResource(
                    if (entry.reversed) R.string.ledger_reversed_at else R.string.ledger_recorded_at,
                    entry.occurredAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                    entry.source.lowercase().replaceFirstChar(Char::uppercase),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.editable) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.history_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun metricLabel(metric: HabitMetric, otherLabel: String): String = when (metric) {
    HabitMetric.CIGARETTES -> stringResource(R.string.ledger_cigarettes)
    HabitMetric.COFFEE -> stringResource(R.string.beverage_coffee)
    HabitMetric.ALCOHOL -> stringResource(R.string.beverage_alcohol)
    HabitMetric.OTHER -> otherLabel
    HabitMetric.CHECK_INS -> stringResource(R.string.ledger_check_ins)
    HabitMetric.STEPS -> stringResource(R.string.ledger_steps)
}

internal fun metricIcon(metric: HabitMetric): ImageVector = when (metric) {
    HabitMetric.CIGARETTES -> Icons.Outlined.SmokingRooms
    HabitMetric.COFFEE -> Icons.Outlined.Coffee
    HabitMetric.ALCOHOL -> Icons.Outlined.LocalBar
    HabitMetric.OTHER -> Icons.Outlined.LocalDrink
    HabitMetric.CHECK_INS -> Icons.Outlined.Psychology
    HabitMetric.STEPS -> Icons.AutoMirrored.Outlined.DirectionsWalk
}

@Composable
internal fun metricContainer(metric: HabitMetric): Color = when (metric) {
    HabitMetric.CIGARETTES -> MaterialTheme.colorScheme.errorContainer
    HabitMetric.COFFEE -> MaterialTheme.colorScheme.primaryContainer
    HabitMetric.ALCOHOL -> MaterialTheme.colorScheme.tertiaryContainer
    HabitMetric.OTHER -> MaterialTheme.colorScheme.secondaryContainer
    HabitMetric.CHECK_INS -> MaterialTheme.colorScheme.secondaryContainer
    HabitMetric.STEPS -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
internal fun metricContent(metric: HabitMetric): Color = when (metric) {
    HabitMetric.CIGARETTES -> MaterialTheme.colorScheme.onErrorContainer
    HabitMetric.COFFEE -> MaterialTheme.colorScheme.onPrimaryContainer
    HabitMetric.ALCOHOL -> MaterialTheme.colorScheme.onTertiaryContainer
    HabitMetric.OTHER -> MaterialTheme.colorScheme.onSecondaryContainer
    HabitMetric.CHECK_INS -> MaterialTheme.colorScheme.onSecondaryContainer
    HabitMetric.STEPS -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
private fun ledgerFilterLabel(filter: LedgerFilter): String = stringResource(
    when (filter) {
        LedgerFilter.ALL -> R.string.ledger_filter_all
        LedgerFilter.CIGARETTES -> R.string.ledger_filter_cigarettes
        LedgerFilter.DRINKS -> R.string.ledger_filter_drinks
        LedgerFilter.CHECK_INS -> R.string.ledger_filter_check_ins
    },
)
