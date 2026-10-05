package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.domain.IfThenPlan
import com.pace.reduction.domain.Playbook

/**
 * Decisions made while it is calm, for the minute when it is not.
 *
 * The coach drafts plans from what it can see of the day — the stretch that keeps being hard, the
 * situations that come up — and the person keeps the ones that sound like them. Kept plans sit on
 * Today and are handed back to the coach, which reminds them of their own words when a cue lands.
 */
@Composable
internal fun PlaybookScreen(
    uiState: PaceUiState,
    viewModel: PaceViewModel,
    onBack: () -> Unit,
) {
    val playbook by viewModel.extras.playbook.collectAsStateWithLifecycle()
    val plans = uiState.settings.ifThenPlans
    var cue by rememberSaveable { mutableStateOf("") }
    var action by rememberSaveable { mutableStateOf("") }
    val full = plans.size >= Playbook.MAX_PLANS

    PaceScreen(
        title = stringResource(R.string.playbook_title),
        onBack = onBack,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LeadParagraph(stringResource(R.string.playbook_intro)) }
        item {
            SectionCard {
                Text(stringResource(R.string.playbook_yours), style = MaterialTheme.typography.titleLarge)
                if (plans.isEmpty()) {
                    Text(
                        stringResource(R.string.playbook_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                plans.forEachIndexed { index, plan ->
                    if (index > 0) HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IfThenText(plan, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.extras.savePlans(plans - plan) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.playbook_remove))
                        }
                    }
                }
            }
        }
        if (uiState.ai.isReady) item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.playbook_draft_title), style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.playbook_draft_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                playbook.suggestions.forEach { suggestion ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            IfThenText(suggestion)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.extras.keepSuggestion(suggestion) },
                                    enabled = !full,
                                ) { Text(stringResource(R.string.playbook_keep)) }
                                TextButton(onClick = { viewModel.extras.dismissSuggestion(suggestion) }) {
                                    Text(stringResource(R.string.playbook_skip))
                                }
                            }
                        }
                    }
                }
                playbook.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (playbook.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.playbook_drafting), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel.extras::draftPlaybook,
                        enabled = !full,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (playbook.suggestions.isEmpty()) R.string.playbook_draft else R.string.playbook_draft_more,
                            ),
                        )
                    }
                }
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.playbook_add_title), style = MaterialTheme.typography.titleLarge)
                if (full) {
                    Text(
                        stringResource(R.string.playbook_full, Playbook.MAX_PLANS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = cue,
                        onValueChange = { cue = it.take(Playbook.MAX_PART_CHARS) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.playbook_if_label)) },
                        placeholder = { Text(stringResource(R.string.playbook_if_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = action,
                        onValueChange = { action = it.take(Playbook.MAX_PART_CHARS) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.playbook_then_label)) },
                        placeholder = { Text(stringResource(R.string.playbook_then_hint)) },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            viewModel.extras.savePlans(plans + IfThenPlan(cue, action))
                            cue = ""
                            action = ""
                        },
                        enabled = cue.isNotBlank() && action.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.playbook_add))
                    }
                }
            }
        }
    }
}

/** "If … then …" with the two halves set apart, so a plan reads in the glance it gets on Today. */
@Composable
internal fun IfThenText(plan: IfThenPlan, modifier: Modifier = Modifier) {
    val ifWord = stringResource(R.string.playbook_if)
    val thenWord = stringResource(R.string.playbook_then)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) {
                append(ifWord)
                append(' ')
            }
            append(plan.cue)
            append(", ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) {
                append(thenWord)
                append(' ')
            }
            append(plan.action)
        },
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Today's view of the playbook: a handful of plans, or an invitation to make the first one. */
@Composable
internal fun PlaybookCard(
    plans: List<IfThenPlan>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.playbook_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) {
                Text(stringResource(if (plans.isEmpty()) R.string.playbook_make else R.string.playbook_edit))
            }
        }
        if (plans.isEmpty()) {
            Text(
                stringResource(R.string.playbook_card_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            plans.take(3).forEach { IfThenText(it) }
            if (plans.size > 3) {
                Text(
                    stringResource(R.string.playbook_more, plans.size - 3),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
