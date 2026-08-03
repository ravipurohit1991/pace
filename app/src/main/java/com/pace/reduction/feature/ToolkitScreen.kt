package com.pace.reduction.feature

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.core.network.SafeLinks
import java.time.Duration
import kotlinx.coroutines.delay

@Composable
internal fun EnhancedToolkitScreen(uiState: PaceUiState, viewModel: PaceViewModel) {
    var urgeBefore by rememberSaveable { mutableIntStateOf(3) }
    var note by rememberSaveable { mutableStateOf("") }
    var triggers by rememberSaveable { mutableStateOf(listOf<String>()) }
    var activeTool by rememberSaveable { mutableStateOf<String?>(null) }
    var completedTool by rememberSaveable { mutableStateOf<String?>(null) }
    var externalPending by rememberSaveable { mutableStateOf<String?>(null) }
    var externalWasBackgrounded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val trustedPersonMessage = stringResource(R.string.trusted_person_message)
    val shareChooserTitle = stringResource(R.string.share_with_someone)
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val activePause = uiState.activePause
    val pauseRemaining = when {
        activePause.endAt != null -> Duration.between(uiState.now, activePause.endAt).toMillis().coerceAtLeast(0L)
        activePause.isPaused -> activePause.pausedRemainingMillis
        else -> 0L
    }
    val pendingPauseOutcome = uiState.urgeSessions.firstOrNull {
        it.tool == "PAUSE" && it.completed && it.urgeAfter == null
    }

    LaunchedEffect(activePause.sessionId, activePause.endAt, pauseRemaining) {
        if (activePause.isRunning && pauseRemaining == 0L) {
            viewModel.completePause()
            if (uiState.settings.hapticsEnabled) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
        }
    }

    DisposableEffect(lifecycleOwner, externalPending) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (externalPending != null) externalWasBackgrounded = true
                Lifecycle.Event.ON_RESUME -> if (externalPending != null && externalWasBackgrounded) {
                    completedTool = "EXTERNAL_GAME:${externalPending}"
                    externalPending = null
                    externalWasBackgrounded = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val triggerOptions = listOf(
        "coffee" to R.string.trigger_coffee,
        "after_food" to R.string.trigger_after_food,
        "stress" to R.string.trigger_stress,
        "alcohol" to R.string.trigger_alcohol,
        "commute" to R.string.trigger_commute,
        "social" to R.string.trigger_social,
        "boredom" to R.string.trigger_boredom,
        "work_break" to R.string.trigger_work_break,
        "place" to R.string.trigger_place,
        "other" to R.string.trigger_other,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.toolkit_title), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.toolkit_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard {
                Text(stringResource(R.string.urge_check_in_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.urge_strength, urgeBefore), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = urgeBefore.toFloat(),
                    onValueChange = { urgeBefore = it.toInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3,
                )
                Text(stringResource(R.string.name_trigger), style = MaterialTheme.typography.titleMedium)
                triggerOptions.chunked(2).forEach { options ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (key, label) ->
                            FilterChip(
                                selected = key in triggers,
                                onClick = { triggers = if (key in triggers) triggers - key else triggers + key },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.optional_note)) },
                )
            }
        }
        item {
            PauseCard(
                active = activePause.isActive,
                paused = activePause.isPaused,
                remainingMillis = pauseRemaining,
                onStart = { viewModel.startPause(urgeBefore, triggers.toSet(), note) },
                onPause = viewModel::pauseTimer,
                onResume = viewModel::resumeTimer,
                onCancel = viewModel::cancelTimer,
            )
        }
        if (pendingPauseOutcome != null) {
            item {
                OutcomeCard(
                    toolTitle = stringResource(R.string.five_minute_pause),
                    onSave = { after, smoked -> viewModel.finishUrgeOutcome(pendingPauseOutcome.id, after, smoked) },
                )
            }
        }
        if (completedTool != null) {
            item {
                OutcomeCard(
                    toolTitle = toolDisplayName(completedTool.orEmpty()),
                    onSave = { after, smoked ->
                        val encoded = completedTool.orEmpty()
                        val tool = encoded.substringBefore(':')
                        val externalRef = encoded.substringAfter(':', "").ifBlank { null }
                        viewModel.saveCompletedTool(
                            tool = tool,
                            urgeBefore = urgeBefore,
                            urgeAfter = after,
                            triggers = triggers.toSet(),
                            note = note,
                            smokedAfter = smoked,
                            externalRef = externalRef,
                        )
                        completedTool = null
                        activeTool = null
                    },
                )
            }
        }
        when (activeTool) {
            "SEQUENCE" -> item { SequenceGame(onComplete = { completedTool = "SEQUENCE" }, onClose = { activeTool = null }) }
            "MEMORY" -> item { MemoryGame(onComplete = { completedTool = "MEMORY" }, onClose = { activeTool = null }) }
            "GROUNDING" -> item { GroundingTool(onComplete = { completedTool = "GROUNDING" }, onClose = { activeTool = null }) }
            "CHANGE_PLACE" -> item { ChangePlaceTool(onComplete = { completedTool = "CHANGE_PLACE" }, onClose = { activeTool = null }) }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.local_tools_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.local_tools_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToolButton(R.string.sequence_title) { activeTool = "SEQUENCE" }
                ToolButton(R.string.memory_title) { activeTool = "MEMORY" }
                ToolButton(R.string.grounding_title) { activeTool = "GROUNDING" }
                ToolButton(R.string.change_place_title) { activeTool = "CHANGE_PLACE" }
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.trusted_person_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.trusted_person_body))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, trustedPersonMessage)
                                },
                                shareChooserTitle,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.share_with_someone))
                }
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.connected_break_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.connected_break_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExternalGameButton(R.string.game_2048, R.string.game_2048_attribution) {
                    externalPending = "original_2048"
                    openTrustedTab(context, "https://gabrielecirulli.github.io/2048/")
                }
                ExternalGameButton(R.string.game_fifteen, R.string.game_fifteen_attribution) {
                    externalPending = "simon_tatham_fifteen"
                    openTrustedTab(context, "https://www.chiark.greenend.org.uk/~sgtatham/puzzles/js/fifteen.html")
                }
                ExternalGameButton(R.string.game_collection, R.string.game_collection_attribution) {
                    externalPending = "simon_tatham_collection"
                    openTrustedTab(context, "https://www.chiark.greenend.org.uk/~sgtatham/puzzles/")
                }
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.guidance_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.guidance_change_scene_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.guidance_change_scene_body))
                Text(stringResource(R.string.guidance_source_reviewed), style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    onClick = { openTrustedTab(context, "https://smokefree.gov/challenges-when-quitting/cravings-triggers/how-manage-cravings") },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.open_official_source))
                }
            }
        }
        item { WeatherAndPlacesSection(uiState, viewModel) }
        item {
            Text(
                stringResource(R.string.professional_support_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PauseCard(
    active: Boolean,
    paused: Boolean,
    remainingMillis: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.five_minute_pause), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (active) formatToolkitTimer(remainingMillis) else stringResource(R.string.pause_invitation),
                style = if (active) MaterialTheme.typography.displayMedium else MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (!active) {
                Button(onClick = onStart) { Text(stringResource(R.string.start_pause)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = if (paused) onResume else onPause) {
                        Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null)
                        Text(stringResource(if (paused) R.string.resume_pause else R.string.pause_timer))
                    }
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel_pause)) }
                }
            }
        }
    }
}

@Composable
private fun OutcomeCard(toolTitle: String, onSave: (Int, Boolean?) -> Unit) {
    var urgeAfter by rememberSaveable { mutableIntStateOf(3) }
    var smoked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    SectionCard {
        Text(stringResource(R.string.after_check_in_title, toolTitle), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.urge_after, urgeAfter))
        Slider(
            value = urgeAfter.toFloat(),
            onValueChange = { urgeAfter = it.toInt().coerceIn(1, 5) },
            valueRange = 1f..5f,
            steps = 3,
        )
        Text(stringResource(R.string.smoked_after_question))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = smoked == false, onClick = { smoked = false }, label = { Text(stringResource(R.string.no)) })
            FilterChip(selected = smoked == true, onClick = { smoked = true }, label = { Text(stringResource(R.string.yes)) })
            FilterChip(selected = smoked == null, onClick = { smoked = null }, label = { Text(stringResource(R.string.prefer_not_to_say)) })
        }
        Button(onClick = { onSave(urgeAfter, smoked) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.save_check_in))
        }
        Text(stringResource(R.string.delay_is_win), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SequenceGame(onComplete: () -> Unit, onClose: () -> Unit) {
    val patterns = listOf(listOf(0, 2, 1), listOf(0, 2, 1, 3), listOf(0, 2, 1, 3, 2))
    var round by rememberSaveable { mutableIntStateOf(0) }
    var inputIndex by rememberSaveable { mutableIntStateOf(0) }
    var showing by rememberSaveable { mutableStateOf(true) }
    var message by rememberSaveable { mutableStateOf("") }
    val pattern = patterns[round]
    val readyText = stringResource(R.string.sequence_your_turn)
    val retryText = stringResource(R.string.sequence_try_again)
    LaunchedEffect(round) {
        showing = true
        delay(1_600)
        showing = false
        message = readyText
    }
    SectionCard {
        Text(stringResource(R.string.sequence_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.round_of_three, round + 1))
        Text(
            if (showing) pattern.joinToString("  ·  ") { (it + 1).toString() } else message,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { column ->
                    val target = row * 2 + column
                    Button(
                        onClick = {
                            if (!showing && target == pattern[inputIndex]) {
                                inputIndex++
                                if (inputIndex == pattern.size) {
                                    if (round == 2) onComplete() else {
                                        round++
                                        inputIndex = 0
                                    }
                                }
                            } else if (!showing) {
                                inputIndex = 0
                                message = retryText
                            }
                        },
                        modifier = Modifier.weight(1f).height(72.dp),
                        enabled = !showing,
                    ) { Text((target + 1).toString(), fontSize = 24.sp) }
                }
            }
        }
        OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close_tool)) }
    }
}

@Composable
private fun MemoryGame(onComplete: () -> Unit, onClose: () -> Unit) {
    val symbols = stringArrayResource(R.array.memory_symbols)
    var deck by rememberSaveable { mutableStateOf((0 until 6).flatMap { listOf(it, it) }.shuffled()) }
    var visible by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var matched by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var moves by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (visible.size == 2) {
            delay(650)
            val first = visible[0]
            val second = visible[1]
            val newlyMatched = if (deck[first] == deck[second]) 2 else 0
            if (newlyMatched > 0) matched = (matched + first + second).distinct()
            visible = emptyList()
            if (matched.size + newlyMatched == 12) onComplete()
        }
    }
    SectionCard {
        Text(stringResource(R.string.memory_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.move_count, moves))
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { column ->
                    val index = row * 4 + column
                    Button(
                        onClick = {
                            if (index !in matched && index !in visible && visible.size < 2) {
                                visible = visible + index
                                if (visible.size == 2) moves++
                            }
                        },
                        modifier = Modifier.weight(1f).height(58.dp),
                    ) {
                        Text(if (index in matched || index in visible) symbols[deck[index]] else stringResource(R.string.memory_hidden))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                deck = (0 until 6).flatMap { listOf(it, it) }.shuffled()
                visible = emptyList()
                matched = emptyList()
                moves = 0
            }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(stringResource(R.string.restart))
            }
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close_tool)) }
        }
    }
}

@Composable
private fun GroundingTool(onComplete: () -> Unit, onClose: () -> Unit) {
    val steps = stringArrayResource(R.array.grounding_steps)
    var step by rememberSaveable { mutableIntStateOf(0) }
    SectionCard {
        Text(stringResource(R.string.grounding_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.step_of_five, step + 1))
        Text(steps[step], style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (step > 0) step-- else onClose() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (step > 0) R.string.back else R.string.close_tool))
            }
            Button(onClick = { if (step < 4) step++ else onComplete() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (step < 4) R.string.next else R.string.complete_tool))
            }
        }
    }
}

@Composable
private fun ChangePlaceTool(onComplete: () -> Unit, onClose: () -> Unit) {
    var endAt by rememberSaveable { mutableLongStateOf(0L) }
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(endAt) {
        while (endAt > 0) {
            remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining == 0L) break
            delay(250)
        }
    }
    SectionCard {
        Text(stringResource(R.string.change_place_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.change_place_body))
        if (endAt == 0L) {
            Button(onClick = { endAt = System.currentTimeMillis() + 2 * 60_000L }) {
                Text(stringResource(R.string.start_two_minute_reset))
            }
        } else {
            Text(formatToolkitTimer(remaining), style = MaterialTheme.typography.displaySmall)
            if (remaining == 0L) Button(onClick = onComplete) { Text(stringResource(R.string.complete_tool)) }
        }
        OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close_tool)) }
    }
}

@Composable
private fun ToolButton(label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Extension, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(label))
    }
}

@Composable
private fun ExternalGameButton(title: Int, attribution: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(attribution), style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
    }
}

@Composable
private fun toolDisplayName(encoded: String): String = when (encoded.substringBefore(':')) {
    "SEQUENCE" -> stringResource(R.string.sequence_title)
    "MEMORY" -> stringResource(R.string.memory_title)
    "GROUNDING" -> stringResource(R.string.grounding_title)
    "CHANGE_PLACE" -> stringResource(R.string.change_place_title)
    "EXTERNAL_GAME" -> stringResource(R.string.connected_break_title)
    else -> stringResource(R.string.toolkit_title)
}

internal fun openTrustedTab(context: android.content.Context, url: String) {
    val uri = SafeLinks.requireAllowed(url)
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
        .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private fun formatToolkitTimer(milliseconds: Long): String {
    val totalSeconds = ((milliseconds + 999) / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
