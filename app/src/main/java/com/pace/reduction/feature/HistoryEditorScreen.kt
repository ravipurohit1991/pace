package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.domain.model.CigaretteLog
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Correcting the record after the fact: add a cigarette you forgot to log, or remove one that was
 * logged twice. Editing a past day also backfills that day's plan snapshot, so the day starts
 * counting toward averages, steady days and the adaptive gap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryEditorScreen(
    uiState: PaceUiState,
    viewModel: PaceViewModel,
    onBack: () -> Unit,
) {
    val date by viewModel.editorDate.collectAsStateWithLifecycle()
    val logs by viewModel.editorLogs.collectAsStateWithLifecycle()
    var hour by rememberSaveable { mutableStateOf("") }
    var minute by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshEditorLogs() }

    val today = LocalDate.now()
    val active = logs.filter { it.reversedAt == null }
    val parsedHour = hour.toIntOrNull()
    val parsedMinute = minute.toIntOrNull()
    val timeValid = parsedHour in 0..23 && parsedMinute in 0..59

    PaceScreen(
        title = stringResource(R.string.history_title),
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
                            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.history_day_count, active.size, uiState.settings.dailyCeiling),
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
            }
        }

        item {
            SectionCard {
                Text(stringResource(R.string.history_add_title), style = MaterialTheme.typography.titleMedium)
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
                Button(
                    onClick = {
                        viewModel.addHistoryEntry(parsedHour ?: 0, parsedMinute ?: 0)
                        hour = ""
                        minute = ""
                    },
                    enabled = timeValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.history_add_action))
                }
                Text(
                    stringResource(R.string.history_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(stringResource(R.string.history_entries), style = MaterialTheme.typography.titleMedium)
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(logs, key = CigaretteLog::id) { log ->
            HistoryRow(log = log, onDelete = { viewModel.deleteHistoryEntry(log.id) })
        }
        }
}

@Composable
private fun HistoryRow(log: CigaretteLog, onDelete: () -> Unit) {
    val reversed = log.reversedAt != null
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                log.occurredAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (reversed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                stringResource(if (reversed) R.string.history_reversed else R.string.history_source, log.source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.history_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    HorizontalDivider()
}
