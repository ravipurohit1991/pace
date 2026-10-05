package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pace.reduction.AutopilotUiState
import com.pace.reduction.R
import com.pace.reduction.domain.AutopilotReason
import com.pace.reduction.domain.ToolDirectory
import com.pace.reduction.domain.ToolStat

/** The user-facing name of anything [ToolDirectory] can open. */
internal fun toolTitleRes(id: String): Int = when (id) {
    ToolDirectory.BREATHING -> R.string.breathing_title
    ToolDirectory.URGE_SURF -> R.string.urge_surf_title
    ToolDirectory.GROUNDING -> R.string.grounding_title
    ToolDirectory.CHANGE_PLACE -> R.string.change_place_title
    ToolDirectory.BLOCKS -> R.string.blocks_title
    ToolDirectory.MEMORY -> R.string.memory_title
    ToolDirectory.SEQUENCE -> R.string.sequence_title
    ToolDirectory.STORY -> R.string.story_title
    ToolDirectory.TRIVIA -> R.string.trivia_title
    ToolDirectory.EMOJI -> R.string.emoji_title
    else -> if (id.startsWith(ToolDirectory.MOVE_PREFIX)) {
        moveTitleRes(id.removePrefix(ToolDirectory.MOVE_PREFIX))
    } else {
        R.string.toolkit_title
    }
}

private fun reasonRes(reason: AutopilotReason): Int = when (reason) {
    AutopilotReason.STRONG_MOMENT -> R.string.autopilot_reason_strong
    AutopilotReason.PROVEN_FOR_YOU -> R.string.autopilot_reason_proven
    AutopilotReason.LATE_HOUR -> R.string.autopilot_reason_late
    AutopilotReason.GET_MOVING -> R.string.autopilot_reason_move
    AutopilotReason.FRESH_DISTRACTION -> R.string.autopilot_reason_fresh
    AutopilotReason.MODEL -> R.string.autopilot_reason_fresh
}

/**
 * "Pick for me": one button for the minute when choosing between eleven tools is itself the
 * obstacle. The coach weighs the hour, the reported strength and what has worked before; with no
 * coach the device does the same from its own records.
 *
 * After a picked tool finishes, the same card asks — optionally — how strong it is now. That one
 * tap is what turns a used tool into evidence the next pick can lean on.
 */
@Composable
internal fun PickForMeCard(
    state: AutopilotUiState,
    coachReady: Boolean,
    onPick: (strength: Int?) -> Unit,
    onStart: (toolId: String) -> Unit,
    onRateAfter: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var strength by rememberSaveable { mutableStateOf<Int?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.autopilot_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            val pick = state.pick
            when {
                state.awaitingRating -> {
                    Text(
                        stringResource(R.string.autopilot_rate_after),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    StrengthChips(selected = null, onSelect = onRateAfter)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.autopilot_skip_rating)) }
                }

                state.busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.autopilot_thinking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                pick != null -> {
                    Text(
                        stringResource(toolTitleRes(pick.toolId)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        pick.message ?: stringResource(reasonRes(pick.reason)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onStart(pick.toolId) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.autopilot_start))
                        }
                        TextButton(onClick = { onPick(state.strength) }) {
                            Text(stringResource(R.string.autopilot_another))
                        }
                    }
                }

                else -> {
                    Text(
                        stringResource(if (coachReady) R.string.autopilot_body else R.string.autopilot_body_offline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        stringResource(R.string.autopilot_strength_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    StrengthChips(selected = strength, onSelect = { strength = if (strength == it) null else it })
                    Button(onClick = { onPick(strength) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.autopilot_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthChips(selected: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.toString()) },
            )
        }
    }
}

/**
 * "Your go-tos": what was used last, one tap to reopen, and — once there is evidence — which tools
 * measurably took the edge off. Built from the device's own records, never sent anywhere.
 */
@Composable
internal fun GoToCard(
    recent: List<String>,
    stats: List<ToolStat>,
    onOpen: (String) -> Unit,
) {
    val helpful = stats.filter { it.rated > 0 && (it.averageDrop ?: 0.0) > 0.0 }.take(3)
    if (recent.isEmpty() && helpful.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    SectionCard {
        Text(stringResource(R.string.goto_title), style = MaterialTheme.typography.titleLarge)
        if (recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.goto_recent), style = MaterialTheme.typography.labelLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { id ->
                    AssistChip(onClick = { onOpen(id) }, label = { Text(stringResource(toolTitleRes(id))) })
                }
            }
        }
        if (helpful.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Insights,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.goto_helps), style = MaterialTheme.typography.labelLarge)
            }
            helpful.forEach { stat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(toolTitleRes(stat.toolId)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(
                                R.string.goto_stat,
                                String.format(locale, "%.1f", stat.averageDrop ?: 0.0),
                                stat.rated,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onOpen(stat.toolId) }) { Text(stringResource(R.string.goto_open)) }
                }
            }
        } else {
            Text(
                stringResource(R.string.goto_helps_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
