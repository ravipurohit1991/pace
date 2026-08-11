package com.pace.reduction.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.domain.MoveCatalogue
import com.pace.reduction.domain.MoveKind
import com.pace.reduction.domain.MoveSession
import com.pace.reduction.domain.tickAt
import kotlinx.coroutines.delay

/**
 * The movement half of the toolkit.
 *
 * Everything else in there works on the head — occupy it, slow it down, watch the wave go past.
 * These work on the body, which is the one thing with a dose behind it: a single short bout blunts
 * the wanting for the half hour that follows, and it does so at intensities as low as a slow walk.
 * So the sessions are short, need no equipment, and the hardest one still fits in the clothes
 * somebody is already wearing.
 */

/**
 * Elapsed milliseconds at a resolution these sessions actually need.
 *
 * The breathing orb reads the frame clock because it is being breathed along with and a dropped
 * beat is visible. A movement session shows whole seconds for up to fifteen minutes, where sixty
 * recompositions a second would be fifty-nine parts waste — but the value is still read off the
 * wall clock rather than accumulated, so a slow frame cannot make the countdown lie.
 */
@Composable
private fun rememberSessionElapsedMs(runId: Int): Long {
    var elapsed by remember(runId) { mutableLongStateOf(0L) }
    LaunchedEffect(runId) {
        if (runId == 0) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            elapsed = (System.nanoTime() - start) / 1_000_000L
            delay(TICK_MS)
        }
    }
    return elapsed
}

/**
 * One guided session, step by step.
 *
 * [stepsToday] is the pedometer's running total, which the walking sessions difference against the
 * value at the start to show real distance covered. It is null when step counting is off, and the
 * session simply falls back to the clock — a walk somebody actually took still counts when the
 * phone was not watching.
 */
@Composable
internal fun MoveSessionTool(
    session: MoveSession,
    hapticsEnabled: Boolean,
    stepsToday: Long?,
    onSampleSteps: () -> Unit,
    onComplete: (urgeBefore: Int?, steps: Long) -> Unit,
    onRateAfter: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var runId by rememberSaveable(session.id) { mutableStateOf(0) }
    var urgeBefore by rememberSaveable(session.id) { mutableStateOf<Int?>(null) }
    var urgeAfter by rememberSaveable(session.id) { mutableStateOf<Int?>(null) }
    var finished by rememberSaveable(session.id) { mutableStateOf(false) }
    var baselineSteps by rememberSaveable(session.id) { mutableStateOf(-1L) }
    var saved by rememberSaveable(session.id) { mutableStateOf(false) }

    val elapsed = rememberSessionElapsedMs(runId)
    val running = runId != 0 && !finished
    val tick = session.tickAt(elapsed)
    val haptics = LocalHapticFeedback.current
    val walked = if (baselineSteps < 0 || stepsToday == null) 0L else (stepsToday - baselineSteps).coerceAtLeast(0L)

    // A short buzz on each new instruction, so the session can be followed with the phone in a
    // pocket — which is where a phone belongs during a walk.
    LaunchedEffect(runId, tick.index) {
        if (running && hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(runId, tick.finished) {
        if (running && tick.finished) finished = true
    }
    // Only while a pedometer-verified session is actually running — the loop stops the moment the
    // session ends rather than sampling on until the card is closed.
    LaunchedEffect(running, session.id) {
        if (!running || !session.verifiedByPedometer || stepsToday == null) return@LaunchedEffect
        while (true) {
            onSampleSteps()
            delay(STEP_SAMPLE_MS)
        }
    }
    // Written the moment the session ends, whichever way it ended, so closing the card is never how
    // a finished session gets lost. The after-rating patches that same row rather than writing a
    // second one — which is why it can be optional without costing anything.
    LaunchedEffect(finished) {
        if (finished && !saved) {
            saved = true
            onComplete(urgeBefore, walked)
        }
    }

    ToolShell(
        title = stringResource(moveTitleRes(session.id)),
        evidence = stringResource(R.string.move_evidence),
        onClose = onClose,
    ) {
        when {
            !running && !finished -> MoveIntro(
                session = session,
                urgeBefore = urgeBefore,
                onRate = { urgeBefore = it },
                onStart = {
                    baselineSteps = stepsToday ?: -1L
                    runId = 1
                },
            )

            running -> MoveRunning(session = session, elapsed = elapsed, walked = walked, showSteps = stepsToday != null) {
                finished = true
            }

            else -> MoveFinished(
                session = session,
                walked = walked,
                showSteps = stepsToday != null,
                urgeBefore = urgeBefore,
                urgeAfter = urgeAfter,
                onRate = { rating ->
                    urgeAfter = rating
                    onRateAfter(rating)
                },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun MoveIntro(
    session: MoveSession,
    urgeBefore: Int?,
    onRate: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Text(stringResource(moveSummaryRes(session.id)), style = MaterialTheme.typography.bodyMedium)
    Text(
        text = if (session.verifiedByPedometer) {
            stringResource(R.string.move_intro_walk, session.minutes, session.stepTarget)
        } else {
            stringResource(R.string.move_intro_steps, session.minutes, session.steps.size)
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    // Asked before the effort, never after it: a rating is one tap while somebody is deciding, and
    // an interrogation once they are out of breath.
    UrgeRatingRow(
        prompt = stringResource(R.string.move_rate_before),
        selected = urgeBefore,
        onSelect = onRate,
    )
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.move_start))
    }
}

@Composable
private fun MoveRunning(
    session: MoveSession,
    elapsed: Long,
    walked: Long,
    showSteps: Boolean,
    onFinishEarly: () -> Unit,
) {
    val tick = session.tickAt(elapsed)
    val step = session.steps[tick.index]
    val motion = LocalMotion.current
    val sessionProgress by animateFloatAsState(tick.sessionProgress, motion.eased(400), label = "moveSession")

    Text(
        stringResource(R.string.move_step_of, tick.index + 1, session.steps.size),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(moveStepTitleRes(step.id)),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(stringResource(moveStepCueRes(step.id)), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(2.dp))
    Text(
        tick.secondsLeftInStep.toString(),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    LinearProgressIndicator(progress = { tick.stepProgress }, modifier = Modifier.fillMaxWidth())
    session.steps.getOrNull(tick.index + 1)?.let { next ->
        Text(
            stringResource(R.string.move_next_up, stringResource(moveStepTitleRes(next.id))),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { sessionProgress },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary,
    )
    if (session.verifiedByPedometer && showSteps) {
        Text(
            stringResource(R.string.move_steps_so_far, walked, session.stepTarget),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    OutlinedButton(onClick = onFinishEarly, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tool_finish_early))
    }
}

@Composable
private fun MoveFinished(
    session: MoveSession,
    walked: Long,
    showSteps: Boolean,
    urgeBefore: Int?,
    urgeAfter: Int?,
    onRate: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Text(stringResource(R.string.move_done_title), style = MaterialTheme.typography.titleLarge)
    if (session.verifiedByPedometer && showSteps && walked > 0) {
        Text(
            stringResource(R.string.move_done_steps, walked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    UrgeRatingRow(
        prompt = stringResource(R.string.move_rate_after),
        selected = urgeAfter,
        onSelect = onRate,
    )
    // Only once both ends exist is there anything to say. A single number is not a change.
    if (urgeBefore != null && urgeAfter != null) {
        Text(
            text = when {
                urgeAfter < urgeBefore -> stringResource(R.string.move_delta_down, urgeBefore, urgeAfter)
                urgeAfter > urgeBefore -> stringResource(R.string.move_delta_up)
                else -> stringResource(R.string.move_delta_level)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.move_done_close))
    }
}

/** One to five, unlabelled at the ends because the scale only has to be consistent with itself. */
@Composable
private fun UrgeRatingRow(prompt: String, selected: Int?, onSelect: (Int) -> Unit) {
    Text(
        prompt,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.toString()) },
            )
        }
    }
}

/** The movement shelf on the Toolkit, grouped so the three-minute option is the first one seen. */
@Composable
internal fun MoveShelf(sessions: List<MoveSession>, onOpen: (String) -> Unit) {
    SectionCard {
        Text(stringResource(R.string.move_shelf_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.move_shelf_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        sessions.forEach { session -> MoveRow(session) { onOpen(session.id) } }
    }
}

@Composable
private fun MoveRow(session: MoveSession, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    moveKindIcon(session.kind),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(moveTitleRes(session.id)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(moveSummaryRes(session.id)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.tool_duration_minutes, session.minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun moveKindIcon(kind: MoveKind): ImageVector = when (kind) {
    MoveKind.WALK -> Icons.AutoMirrored.Outlined.DirectionsWalk
    MoveKind.RUN -> Icons.AutoMirrored.Outlined.DirectionsRun
    MoveKind.STRENGTH -> Icons.Outlined.FitnessCenter
    MoveKind.MOBILITY -> Icons.Outlined.SelfImprovement
}

internal fun moveTitleRes(id: String): Int = when (id) {
    MoveCatalogue.WALK_RESET -> R.string.move_walk_reset_title
    MoveCatalogue.WALK_LONG -> R.string.move_walk_long_title
    MoveCatalogue.RUN_INTERVALS -> R.string.move_run_title
    MoveCatalogue.PUSHUP_LADDER -> R.string.move_pushup_title
    MoveCatalogue.CIRCUIT -> R.string.move_circuit_title
    MoveCatalogue.YOGA_MORNING -> R.string.move_yoga_morning_title
    MoveCatalogue.YOGA_WIND_DOWN -> R.string.move_yoga_evening_title
    else -> R.string.move_desk_title
}

internal fun moveSummaryRes(id: String): Int = when (id) {
    MoveCatalogue.WALK_RESET -> R.string.move_walk_reset_summary
    MoveCatalogue.WALK_LONG -> R.string.move_walk_long_summary
    MoveCatalogue.RUN_INTERVALS -> R.string.move_run_summary
    MoveCatalogue.PUSHUP_LADDER -> R.string.move_pushup_summary
    MoveCatalogue.CIRCUIT -> R.string.move_circuit_summary
    MoveCatalogue.YOGA_MORNING -> R.string.move_yoga_morning_summary
    MoveCatalogue.YOGA_WIND_DOWN -> R.string.move_yoga_evening_summary
    else -> R.string.move_desk_summary
}

private fun moveStepTitleRes(id: String): Int = when (id) {
    "walk_out" -> R.string.step_walk_out
    "walk_back" -> R.string.step_walk_back
    "walk_easy" -> R.string.step_walk_easy
    "walk_brisk" -> R.string.step_walk_brisk
    "walk_notice" -> R.string.step_walk_notice
    "run_warm" -> R.string.step_run_warm
    "run_push" -> R.string.step_run_push
    "run_float" -> R.string.step_run_float
    "run_cool" -> R.string.step_run_cool
    "pushup_prep" -> R.string.step_pushup_prep
    "pushup_five" -> R.string.step_pushup_five
    "pushup_eight" -> R.string.step_pushup_eight
    "pushup_ten" -> R.string.step_pushup_ten
    "pushup_max" -> R.string.step_pushup_max
    "pushup_finish" -> R.string.step_pushup_finish
    "rest_shake" -> R.string.step_rest_shake
    "circuit_march" -> R.string.step_circuit_march
    "circuit_squat" -> R.string.step_circuit_squat
    "circuit_pushup" -> R.string.step_circuit_pushup
    "circuit_plank" -> R.string.step_circuit_plank
    "circuit_breathe" -> R.string.step_circuit_breathe
    "yoga_stand_tall" -> R.string.step_yoga_stand_tall
    "yoga_cat_cow" -> R.string.step_yoga_cat_cow
    "yoga_down_dog" -> R.string.step_yoga_down_dog
    "yoga_lunge_left" -> R.string.step_yoga_lunge_left
    "yoga_lunge_right" -> R.string.step_yoga_lunge_right
    "yoga_forward_fold" -> R.string.step_yoga_forward_fold
    "yoga_chest_open" -> R.string.step_yoga_chest_open
    "yoga_reach_up" -> R.string.step_yoga_reach_up
    "yoga_child" -> R.string.step_yoga_child
    "yoga_twist_left" -> R.string.step_yoga_twist_left
    "yoga_twist_right" -> R.string.step_yoga_twist_right
    "yoga_figure_four_left" -> R.string.step_yoga_figure_four_left
    "yoga_figure_four_right" -> R.string.step_yoga_figure_four_right
    "yoga_legs_up" -> R.string.step_yoga_legs_up
    "yoga_settle" -> R.string.step_yoga_settle
    "desk_stand" -> R.string.step_desk_stand
    "desk_shoulder_rolls" -> R.string.step_desk_shoulder_rolls
    "desk_neck_left" -> R.string.step_desk_neck_left
    "desk_neck_right" -> R.string.step_desk_neck_right
    "desk_chest_open" -> R.string.step_desk_chest_open
    else -> R.string.step_desk_twist
}

private fun moveStepCueRes(id: String): Int = when (id) {
    "walk_out" -> R.string.cue_walk_out
    "walk_back" -> R.string.cue_walk_back
    "walk_easy" -> R.string.cue_walk_easy
    "walk_brisk" -> R.string.cue_walk_brisk
    "walk_notice" -> R.string.cue_walk_notice
    "run_warm" -> R.string.cue_run_warm
    "run_push" -> R.string.cue_run_push
    "run_float" -> R.string.cue_run_float
    "run_cool" -> R.string.cue_run_cool
    "pushup_prep" -> R.string.cue_pushup_prep
    "pushup_five" -> R.string.cue_pushup_five
    "pushup_eight" -> R.string.cue_pushup_eight
    "pushup_ten" -> R.string.cue_pushup_ten
    "pushup_max" -> R.string.cue_pushup_max
    "pushup_finish" -> R.string.cue_pushup_finish
    "rest_shake" -> R.string.cue_rest_shake
    "circuit_march" -> R.string.cue_circuit_march
    "circuit_squat" -> R.string.cue_circuit_squat
    "circuit_pushup" -> R.string.cue_circuit_pushup
    "circuit_plank" -> R.string.cue_circuit_plank
    "circuit_breathe" -> R.string.cue_circuit_breathe
    "yoga_stand_tall" -> R.string.cue_yoga_stand_tall
    "yoga_cat_cow" -> R.string.cue_yoga_cat_cow
    "yoga_down_dog" -> R.string.cue_yoga_down_dog
    "yoga_lunge_left" -> R.string.cue_yoga_lunge_left
    "yoga_lunge_right" -> R.string.cue_yoga_lunge_right
    "yoga_forward_fold" -> R.string.cue_yoga_forward_fold
    "yoga_chest_open" -> R.string.cue_yoga_chest_open
    "yoga_reach_up" -> R.string.cue_yoga_reach_up
    "yoga_child" -> R.string.cue_yoga_child
    "yoga_twist_left" -> R.string.cue_yoga_twist_left
    "yoga_twist_right" -> R.string.cue_yoga_twist_right
    "yoga_figure_four_left" -> R.string.cue_yoga_figure_four_left
    "yoga_figure_four_right" -> R.string.cue_yoga_figure_four_right
    "yoga_legs_up" -> R.string.cue_yoga_legs_up
    "yoga_settle" -> R.string.cue_yoga_settle
    "desk_stand" -> R.string.cue_desk_stand
    "desk_shoulder_rolls" -> R.string.cue_desk_shoulder_rolls
    "desk_neck_left" -> R.string.cue_desk_neck_left
    "desk_neck_right" -> R.string.cue_desk_neck_right
    "desk_chest_open" -> R.string.cue_desk_chest_open
    else -> R.string.cue_desk_twist
}

/** Smooth enough for a progress bar, far cheaper than the frame clock the breath pacer needs. */
private const val TICK_MS = 200L

/** The pedometer is read this often during a walk; it is a cheap on-demand sample, not a listener. */
private const val STEP_SAMPLE_MS = 10_000L
