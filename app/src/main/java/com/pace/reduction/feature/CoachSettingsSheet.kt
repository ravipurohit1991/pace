package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.domain.CoachPrompt
import com.pace.reduction.domain.model.AiSettings

private val INTERVAL_CHOICES = listOf(60, 120, 180, 360)

/**
 * Coach behaviour lives next to the conversation rather than buried in app settings, because it is
 * something you tweak while chatting: the persona, whether your numbers are shared, and check-ins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachSettingsSheet(
    ai: AiSettings,
    viewModel: PaceViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var prompt by rememberSaveable(ai.systemPrompt) {
        mutableStateOf(ai.systemPrompt.ifBlank { CoachPrompt.DEFAULT_PERSONA })
    }
    var includeStats by rememberSaveable(ai.includeStats) { mutableStateOf(ai.includeStats) }
    var checkups by rememberSaveable(ai.checkupsEnabled) { mutableStateOf(ai.checkupsEnabled) }
    var interval by rememberSaveable(ai.checkupIntervalMinutes) { mutableIntStateOf(ai.checkupIntervalMinutes) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.coach_behaviour_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.coach_personality), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoachPrompt.PERSONA_PRESETS.forEach { (label, text) ->
                    FilterChip(
                        selected = prompt.trim() == text,
                        onClick = { prompt = text },
                        label = { Text(label) },
                    )
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it.take(2_000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                label = { Text(stringResource(R.string.coach_system_prompt)) },
                supportingText = { Text(stringResource(R.string.coach_system_prompt_support)) },
            )
            OutlinedButton(
                onClick = { prompt = CoachPrompt.DEFAULT_PERSONA },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.coach_reset_prompt)) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.coach_include_stats), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.coach_include_stats_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = includeStats, onCheckedChange = { includeStats = it })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.coach_checkups), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.coach_checkups_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = checkups, onCheckedChange = { checkups = it })
            }

            if (checkups) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    INTERVAL_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = interval == minutes,
                            onClick = { interval = minutes },
                            label = { Text(stringResource(R.string.coach_checkup_every, formatInterval(minutes))) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val persona = prompt.trim()
                    viewModel.saveCoachBehaviour(
                        systemPrompt = if (persona == CoachPrompt.DEFAULT_PERSONA) "" else persona,
                        includeStats = includeStats,
                        checkupsEnabled = checkups,
                        checkupIntervalMinutes = interval,
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save_settings)) }
        }
    }
}

private fun formatInterval(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"
