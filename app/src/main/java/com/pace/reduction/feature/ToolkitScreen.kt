package com.pace.reduction.feature

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.core.designsystem.breathe
import com.pace.reduction.core.network.SafeLinks
import com.pace.reduction.domain.MoveCatalogue
import java.time.Duration
import kotlinx.coroutines.delay

@Composable
internal fun EnhancedToolkitScreen(
    uiState: PaceUiState,
    viewModel: PaceViewModel,
    onOpenSettings: () -> Unit,
    requestedMoveSession: String? = null,
) {
    val coach by viewModel.coachState.collectAsStateWithLifecycle()
    // An invitation to move opens the session it named, rather than dropping the user on a shelf to
    // find it again — the ask was already made and answered once.
    var activeMove by rememberSaveable { mutableStateOf(requestedMoveSession) }
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

    // Finishing a tool is the whole win; asking a follow-up question straight afterwards was
    // friction at the worst moment, so completion just records itself.
    LaunchedEffect(completedTool) {
        val encoded = completedTool ?: return@LaunchedEffect
        viewModel.saveCompletedTool(
            tool = encoded.substringBefore(':'),
            urgeBefore = null,
            urgeAfter = null,
            triggers = emptySet(),
            note = "",
            smokedAfter = null,
            externalRef = encoded.substringAfter(':', "").ifBlank { null },
        )
        completedTool = null
        activeTool = null
    }

    // An open tool or session takes over the whole screen, so back closes it — the same thing its
    // own close button does — rather than skipping past it to the previous screen.
    BackHandler(enabled = activeMove != null || activeTool != null) {
        if (activeMove != null) activeMove = null else activeTool = null
    }

    val move = activeMove?.let(MoveCatalogue::byId)
    // The pedometer's running total, or null when there is nothing to difference against. A walk
    // still works untracked; it just cannot show its own distance.
    val stepsToday = uiState.steps.takeIf { it.enabled && it.permissionGranted }?.todaySteps

    PaceScreen(
        title = stringResource(R.string.toolkit_title),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (activeTool == null && move == null) item { LeadParagraph(stringResource(R.string.toolkit_intro)) }
        if (move != null) {
            item {
                MoveSessionTool(
                    session = move,
                    hapticsEnabled = uiState.settings.hapticsEnabled,
                    stepsToday = stepsToday,
                    onSampleSteps = viewModel::sampleSteps,
                    onComplete = { urgeBefore, steps ->
                        viewModel.saveCompletedMove(move.id, urgeBefore, steps)
                    },
                    onRateAfter = viewModel::rateLastMove,
                    onClose = { activeMove = null },
                )
            }
            // A session on screen owns the screen, for the same reason an open tool does.
            return@PaceScreen
        }
        // While a tool is open the rescue card only earns its space if a pause is actually
        // running — otherwise it is a second timer competing with the one being used.
        if (activeTool == null || activePause.isActive) {
            item {
                RescueCard(
                    plan = coach.rescuePlan,
                    error = coach.error,
                    busy = coach.rescueBusy,
                    aiReady = uiState.ai.isReady,
                    timerActive = activePause.isActive,
                    timerPaused = activePause.isPaused,
                    remainingMillis = pauseRemaining,
                    onAsk = viewModel::requestRescuePlan,
                    onStartTimer = { viewModel.startPause(null, emptySet(), "") },
                    onPauseTimer = viewModel::pauseTimer,
                    onResumeTimer = viewModel::resumeTimer,
                    onCancelTimer = viewModel::cancelTimer,
                )
            }
        }
        val hapticsEnabled = uiState.settings.hapticsEnabled
        when (activeTool) {
            ToolCatalogue.BREATHING -> item {
                BreathingTool(
                    hapticsEnabled = hapticsEnabled,
                    onComplete = { completedTool = ToolCatalogue.BREATHING },
                    onClose = { activeTool = null },
                )
            }
            ToolCatalogue.URGE_SURF -> item {
                UrgeSurfTool(
                    onComplete = { completedTool = ToolCatalogue.URGE_SURF },
                    onClose = { activeTool = null },
                )
            }
            ToolCatalogue.BLOCKS -> item {
                BlockPuzzleTool(
                    hapticsEnabled = hapticsEnabled,
                    onComplete = { completedTool = ToolCatalogue.BLOCKS },
                    onClose = { activeTool = null },
                )
            }
            ToolCatalogue.SEQUENCE -> item {
                SequenceGame(onComplete = { completedTool = ToolCatalogue.SEQUENCE }, onClose = { activeTool = null })
            }
            ToolCatalogue.MEMORY -> item {
                MemoryGame(onComplete = { completedTool = ToolCatalogue.MEMORY }, onClose = { activeTool = null })
            }
            ToolCatalogue.GROUNDING -> item {
                GroundingTool(onComplete = { completedTool = ToolCatalogue.GROUNDING }, onClose = { activeTool = null })
            }
            ToolCatalogue.CHANGE_PLACE -> item {
                ChangePlaceTool(onComplete = { completedTool = ToolCatalogue.CHANGE_PLACE }, onClose = { activeTool = null })
            }
        }
        // The rest of the screen is a menu, and a menu behind an open tool is just something else
        // to look at when the whole point was to look at one thing.
        if (activeTool != null) return@PaceScreen

        item {
            ToolShelf(
                title = stringResource(R.string.tools_guided_title),
                body = stringResource(R.string.tools_guided_body),
                entries = ToolCatalogue.guided,
                onOpen = { activeTool = it },
            )
        }
        item {
            // Above the games on purpose. A game passes the time; this is the only shelf here with a
            // dose-response behind it, and it should not be the one you scroll to find.
            MoveShelf(sessions = MoveCatalogue.shelf, onOpen = { activeMove = it })
        }
        item {
            ToolShelf(
                title = stringResource(R.string.local_tools_title),
                body = stringResource(R.string.local_tools_body),
                entries = ToolCatalogue.games,
                onOpen = { activeTool = it },
            )
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
        item {
            Text(
                stringResource(R.string.professional_support_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A blank five-minute countdown asks the user to invent their own distraction at the exact moment
 * they are least able to. Instead the coach proposes one specific thing, and the timer is offered
 * afterwards to hold the shape of it.
 */
@Composable
private fun RescueCard(
    plan: String,
    error: String?,
    busy: Boolean,
    aiReady: Boolean,
    timerActive: Boolean,
    timerPaused: Boolean,
    remainingMillis: Long,
    onAsk: () -> Unit,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onCancelTimer: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.rescue_title), style = MaterialTheme.typography.titleLarge)

            when {
                timerActive -> {
                    if (plan.isNotBlank()) {
                        Text(plan, style = MaterialTheme.typography.bodyLarge)
                    }
                    BreathingTimer(
                        remainingMillis = remainingMillis,
                        totalMillis = PAUSE_LENGTH_MS,
                        paused = timerPaused,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = if (timerPaused) onResumeTimer else onPauseTimer,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                if (timerPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                contentDescription = null,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(if (timerPaused) R.string.resume_pause else R.string.pause_timer))
                        }
                        OutlinedButton(onClick = onCancelTimer, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.cancel_pause))
                        }
                    }
                }

                busy -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.rescue_thinking), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                plan.isNotBlank() -> {
                    Text(plan, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartTimer, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.rescue_start_timer))
                        }
                        OutlinedButton(onClick = onAsk, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.rescue_another))
                        }
                    }
                }

                else -> {
                    Text(
                        stringResource(if (aiReady) R.string.rescue_body else R.string.rescue_offline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (aiReady) {
                        Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.rescue_action))
                        }
                    } else {
                        OutlinedButton(onClick = onStartTimer, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.rescue_start_timer))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The five-minute pause, drawn as a ring that empties while a circle inside it breathes.
 *
 * The breathing is not decoration. A craving timer is watched, and a bare digit clock invites the
 * user to count the seconds down; something with a slow, regular rhythm gives them a pace to
 * follow instead — which is the whole intervention this screen is for. It stops the moment the
 * timer is paused, so the animation always reflects whether time is actually running.
 */
@Composable
private fun BreathingTimer(
    remainingMillis: Long,
    totalMillis: Long,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val fraction = if (totalMillis <= 0L) 0f else (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, motion.eased(900), label = "pauseRing")
    val ringColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Box(modifier = modifier.height(210.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .then(if (paused) Modifier else Modifier.breathe(0.82f, 1.06f, 4_000))
                .background(fill, CircleShape),
        )
        Canvas(modifier = Modifier.size(196.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            formatToolkitTimer(remainingMillis),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
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
    ToolShell(
        title = stringResource(R.string.sequence_title),
        evidence = stringResource(R.string.sequence_summary),
        onClose = onClose,
    ) {
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
    ToolShell(
        title = stringResource(R.string.memory_title),
        evidence = stringResource(R.string.memory_summary),
        onClose = onClose,
    ) {
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
        OutlinedButton(
            onClick = {
                deck = (0 until 6).flatMap { listOf(it, it) }.shuffled()
                visible = emptyList()
                matched = emptyList()
                moves = 0
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.restart))
        }
    }
}

@Composable
private fun GroundingTool(onComplete: () -> Unit, onClose: () -> Unit) {
    val steps = stringArrayResource(R.array.grounding_steps)
    var step by rememberSaveable { mutableIntStateOf(0) }
    ToolShell(
        title = stringResource(R.string.grounding_title),
        evidence = stringResource(R.string.grounding_summary),
        onClose = onClose,
    ) {
        Text(stringResource(R.string.step_of_five, step + 1))
        Text(steps[step], style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { step-- },
                enabled = step > 0,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.back))
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
    ToolShell(
        title = stringResource(R.string.change_place_title),
        evidence = stringResource(R.string.guidance_change_scene_body),
        onClose = onClose,
    ) {
        Text(stringResource(R.string.change_place_body))
        if (endAt == 0L) {
            Button(
                onClick = { endAt = System.currentTimeMillis() + 2 * 60_000L },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.start_two_minute_reset))
            }
        } else {
            Text(
                formatToolkitTimer(remaining),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (remaining == 0L) {
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.complete_tool))
                }
            }
        }
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

internal fun openTrustedTab(context: android.content.Context, url: String) {
    val uri = SafeLinks.requireAllowed(url)
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
        .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private fun formatToolkitTimer(milliseconds: Long): String {
    val totalSeconds = ((milliseconds + 999) / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Matches the pause the repository actually starts; the ring needs a denominator to empty against. */
private const val PAUSE_LENGTH_MS = 5 * 60 * 1_000L
