package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.reduction.CoachUiState
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.domain.model.CoachMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachScreen(
    uiState: PaceUiState,
    viewModel: PaceViewModel,
    onOpenSettings: () -> Unit,
) {
    val coach by viewModel.coachState.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var showBehaviourSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val messages = uiState.coachMessages

    LaunchedEffect(messages.size, coach.streamingReply) {
        val target = messages.size + if (coach.busy) 1 else 0
        if (target > 0) listState.animateScrollToItem(target)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.coach_title))
                    Text(
                        stringResource(R.string.coach_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                if (messages.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearCoach) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.coach_clear),
                        )
                    }
                }
                IconButton(onClick = { showBehaviourSheet = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.coach_settings))
                }
            },
        )

        if (showBehaviourSheet) {
            CoachSettingsSheet(
                ai = uiState.ai,
                viewModel = viewModel,
                onDismiss = { showBehaviourSheet = false },
            )
        }

        if (!uiState.ai.isReady) {
            CoachSetupPrompt(onOpenSettings)
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && !coach.busy) {
                item { CoachEmptyState(onStarter = { draft = it }) }
            }
            items(messages, key = CoachMessage::id) { message ->
                ChatBubble(text = message.content, fromUser = message.isUser)
            }
            if (coach.busy) {
                item {
                    if (coach.streamingReply.isBlank()) {
                        ThinkingBubble()
                    } else {
                        ChatBubble(text = coach.streamingReply, fromUser = false)
                    }
                }
            }
            coach.error?.let { error ->
                item { ErrorBubble(error, onDismiss = viewModel::dismissCoachError) }
            }
        }

        CoachComposer(
            draft = draft,
            coach = coach,
            onDraftChange = { draft = it },
            onSend = {
                viewModel.sendCoachMessage(draft)
                draft = ""
            },
            onStop = viewModel::stopCoach,
            onRiddle = viewModel::requestRiddle,
        )
    }
}

@Composable
private fun CoachSetupPrompt(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(36.dp))
                Text(stringResource(R.string.coach_setup_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.coach_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.coach_open_settings)) }
            }
        }
    }
}

@Composable
private fun CoachEmptyState(onStarter: (String) -> Unit) {
    val starters = listOf(
        stringResource(R.string.coach_starter_urge),
        stringResource(R.string.coach_starter_bored),
        stringResource(R.string.coach_starter_why),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.coach_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.coach_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            starters.forEach { starter ->
                AssistChip(onClick = { onStarter(starter) }, label = { Text(starter) })
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, fromUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (fromUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (fromUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (fromUser) 18.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.coach_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBubble(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.dismiss))
            }
        }
    }
}

@Composable
private fun CoachComposer(
    draft: String,
    coach: CoachUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRiddle: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = onRiddle,
                    enabled = !coach.busy,
                    label = { Text(stringResource(R.string.coach_riddle)) },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.coach_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { onDraftChange(it.take(500)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.coach_input_hint)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                )
                FilledIconButton(
                    onClick = if (coach.busy) onStop else onSend,
                    enabled = coach.busy || draft.isNotBlank(),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = if (coach.busy) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(
                            if (coach.busy) R.string.coach_stop else R.string.coach_send,
                        ),
                    )
                }
            }
        }
    }
}
