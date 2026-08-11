package com.pace.reduction.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.core.designsystem.deepened
import com.pace.reduction.core.designsystem.lightened
import com.pace.reduction.domain.BlockPuzzle
import com.pace.reduction.domain.BreathPattern
import com.pace.reduction.domain.BreathPhase
import com.pace.reduction.domain.UrgeWave
import com.pace.reduction.domain.tickAt
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The guided tools, which differ from the small games in that they are doing something to the
 * craving rather than merely occupying the time until it passes.
 *
 * Each one is built the same way: a pure function in `domain` decides what should be happening at a
 * given elapsed time, and everything here only draws it. That is what lets a breath pacer stay
 * honest — the schedule is never inferred from how many frames have gone by.
 */

/** Frames rather than a timer: these tools are watched, so a dropped beat is visible. */
@Composable
private fun rememberElapsedMillis(runId: Int): Long {
    var elapsed by remember(runId) { mutableLongStateOf(0L) }
    LaunchedEffect(runId) {
        if (runId == 0) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame -> elapsed = frame - start }
        }
    }
    return elapsed
}

/**
 * Paced breathing, with the pattern chosen before it starts.
 *
 * The orb keeps moving whatever the motion setting says. Everywhere else in the app movement is
 * decoration and can be turned off; here it is the instruction — a still circle would leave the
 * user with nothing to breathe along with.
 */
@Composable
internal fun BreathingTool(hapticsEnabled: Boolean, onComplete: () -> Unit, onClose: () -> Unit) {
    var patternId by rememberSaveable { mutableStateOf(BreathPattern.BOX.id) }
    var runId by rememberSaveable { mutableStateOf(0) }
    val pattern = BreathPattern.byId(patternId)
    val targetCycles = remember(pattern) { pattern.cyclesFor(BREATHING_TARGET_SECONDS) }
    val elapsed = rememberElapsedMillis(runId)
    val tick = pattern.tickAt(elapsed)
    val haptics = LocalHapticFeedback.current
    val running = runId != 0

    LaunchedEffect(runId, tick.phase) {
        if (running && hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(runId, tick.completedCycles) {
        if (running && tick.completedCycles >= targetCycles) onComplete()
    }

    ToolShell(
        title = stringResource(R.string.breathing_title),
        evidence = stringResource(R.string.breathing_evidence),
        onClose = onClose,
    ) {
        if (!running) {
            // Flowing, not a fixed row: the pattern names carry their counts, and three of them do
            // not fit across a phone without one wrapping into a two-line chip.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BreathPattern.all.forEach { option ->
                    FilterChip(
                        selected = option.id == patternId,
                        onClick = { patternId = option.id },
                        label = { Text(stringResource(breathPatternLabel(option.id))) },
                    )
                }
            }
        }

        BreathOrb(
            scale = tick.scale,
            sessionProgress = if (!running) 0f else {
                (tick.completedCycles.toFloat() / targetCycles).coerceIn(0f, 1f)
            },
            phaseLabel = stringResource(
                if (!running) R.string.breathing_ready else breathPhaseLabel(tick.phase),
            ),
            secondsLeft = if (running) tick.secondsLeftInPhase else null,
        )

        Text(
            text = if (running) {
                stringResource(
                    R.string.breathing_cycles,
                    (tick.completedCycles + 1).coerceAtMost(targetCycles),
                    targetCycles,
                )
            } else {
                // Rounded to the nearest minute so this agrees with the duration on the shelf.
                stringResource(R.string.breathing_ready_body, (pattern.cycleSeconds * targetCycles + 30) / 60)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        if (!running) {
            Button(onClick = { runId = 1 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.breathing_start))
            }
        } else {
            OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tool_finish_early))
            }
        }
    }
}

/** The orb itself: a filled circle that swells with the lungs, inside a ring of session progress. */
@Composable
private fun BreathOrb(scale: Float, sessionProgress: Float, phaseLabel: String, secondsLeft: Int?) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val motion = LocalMotion.current
    val progress by animateFloatAsState(sessionProgress, motion.eased(600), label = "breathSession")

    Box(
        modifier = Modifier.fillMaxWidth().height(228.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(212.dp)) {
            val stroke = 6.dp.toPx()
            val ringRadius = size.minDimension / 2f - stroke / 2f
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Never smaller than a token of itself: an orb that collapses to a point on the exhale
            // reads as the thing vanishing rather than as breath leaving.
            val orbRadius = ringRadius * (0.36f + 0.56f * scale.coerceIn(0f, 1f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.10f)),
                    center = center,
                    radius = orbRadius,
                ),
                radius = orbRadius,
            )
            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = orbRadius,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(phaseLabel, style = MaterialTheme.typography.titleMedium)
            if (secondsLeft != null) {
                Text(
                    secondsLeft.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Urge surfing: three minutes of watching a wave that the user did not draw.
 *
 * The value is entirely in the fall. Anyone in a craving believes it will keep climbing until they
 * give in, so the tool commits to a shape in advance and then keeps its word — the crest arrives
 * about a third of the way through and the rest is the water going back down.
 */
@Composable
internal fun UrgeSurfTool(onComplete: () -> Unit, onClose: () -> Unit) {
    var runId by rememberSaveable { mutableStateOf(0) }
    val elapsed = rememberElapsedMillis(runId)
    val running = runId != 0
    val progress = if (!running) 0f else (elapsed.toFloat() / UrgeWave.DURATION_MS).coerceIn(0f, 1f)
    val stages = stringArrayResource(R.array.urge_surf_stages)
    val height = UrgeWave.height(progress)

    LaunchedEffect(runId, progress) {
        if (running && progress >= 1f) onComplete()
    }

    ToolShell(
        title = stringResource(R.string.urge_surf_title),
        evidence = stringResource(R.string.urge_surf_evidence),
        onClose = onClose,
    ) {
        UrgeWaveCanvas(height = height, phase = elapsed / 900f, cresting = running)
        Text(
            text = if (running) {
                stringResource(
                    when {
                        progress < 0.26f -> R.string.urge_surf_rising
                        progress < 0.42f -> R.string.urge_surf_cresting
                        else -> R.string.urge_surf_falling
                    },
                )
            } else {
                stringResource(R.string.urge_surf_ready)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (running) stages[UrgeWave.stageIndex(progress, stages.size)] else stringResource(R.string.urge_surf_ready_body),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!running) {
            Button(onClick = { runId = 1 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.urge_surf_start))
            }
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tool_finish_early))
            }
        }
    }
}

/** Water, drawn as two offset sine surfaces so the near one reads in front of the far one. */
@Composable
private fun UrgeWaveCanvas(height: Float, phase: Float, cresting: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val backWater = accent.copy(alpha = 0.22f)
    val frontWater = accent.copy(alpha = 0.45f)
    val guide = MaterialTheme.colorScheme.outlineVariant

    // Clipped into a rounded tank. Left to the raw canvas the water met the card's corners in
    // square ones, which read as a chart bar rather than as something with a surface.
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        val baseline = size.height
        // The crest line is a fixed mark on the glass: the wave rising toward it and then leaving
        // it behind is what makes the fall legible without a number.
        drawLine(
            color = guide,
            start = Offset(0f, size.height * 0.16f),
            end = Offset(size.width, size.height * 0.16f),
            strokeWidth = 1.dp.toPx(),
        )

        fun surface(alpha: Float, shift: Float, amplitude: Float, colour: Color) {
            val level = baseline - (size.height * 0.84f) * height * alpha
            val path = Path().apply {
                moveTo(0f, baseline)
                var x = 0f
                while (x <= size.width) {
                    val wobble = sin((x / size.width) * 3.2f * PI.toFloat() + shift) * amplitude
                    lineTo(x, level + wobble)
                    x += 6f
                }
                lineTo(size.width, baseline)
                close()
            }
            drawPath(path, colour)
        }

        surface(alpha = 0.86f, shift = phase * 0.8f, amplitude = 9f, colour = backWater)
        surface(alpha = 1f, shift = phase + 1.4f, amplitude = 6f, colour = frontWater)

        if (cresting) {
            val level = baseline - (size.height * 0.84f) * height
            drawCircle(
                color = accent,
                radius = 5.dp.toPx(),
                center = Offset(size.width / 2f, level + sin(phase + 1.4f + 1.6f) * 6f),
            )
        }
    }
}

/**
 * The falling-block puzzle.
 *
 * Three minutes, no score to chase and no way to lose — the board wipes itself and carries on. The
 * point is the demand it puts on the eye, not the result, and a "game over" screen at minute two of
 * a craving would hand the user a reason to close the app.
 */
@Composable
internal fun BlockPuzzleTool(hapticsEnabled: Boolean, onComplete: () -> Unit, onClose: () -> Unit) {
    var started by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf(BlockPuzzle.start(System.nanoTime())) }
    var remaining by remember { mutableLongStateOf(BLOCKS_SESSION_MS) }
    val haptics = LocalHapticFeedback.current

    fun act(hapticFeedback: Boolean = true, change: () -> Unit) {
        if (!started) return
        change()
        if (hapticFeedback && hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (true) {
            delay(BLOCKS_GRAVITY_MS)
            state = BlockPuzzle.step(state)
        }
    }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        val endAt = System.currentTimeMillis() + BLOCKS_SESSION_MS
        while (true) {
            remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining == 0L) break
            delay(200)
        }
        onComplete()
    }

    ToolShell(
        title = stringResource(R.string.blocks_title),
        evidence = stringResource(R.string.blocks_evidence),
        onClose = onClose,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.blocks_lines, state.linesCleared),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "%d:%02d".format(remaining / 60_000, (remaining % 60_000) / 1_000),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val rotateLabel = stringResource(R.string.blocks_rotate)
        BlockBoard(
            cells = state.render(),
            dimmed = !started,
            modifier = Modifier.clickable(onClickLabel = rotateLabel) {
                act { state = BlockPuzzle.rotate(state) }
            },
        )

        if (!started) {
            Button(onClick = { started = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.blocks_start))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BlockControl(
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    label = R.string.blocks_left,
                    modifier = Modifier.weight(1f),
                ) { act { state = BlockPuzzle.moveLeft(state) } }
                BlockControl(
                    icon = Icons.Outlined.Rotate90DegreesCw,
                    label = R.string.blocks_rotate,
                    modifier = Modifier.weight(1f),
                ) { act { state = BlockPuzzle.rotate(state) } }
                BlockControl(
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    label = R.string.blocks_right,
                    modifier = Modifier.weight(1f),
                ) { act { state = BlockPuzzle.moveRight(state) } }
                BlockControl(
                    icon = Icons.Outlined.KeyboardDoubleArrowDown,
                    label = R.string.blocks_drop,
                    modifier = Modifier.weight(1f),
                ) { act { state = BlockPuzzle.drop(state) } }
            }
            OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tool_finish_early))
            }
        }
    }
}

@Composable
private fun BlockControl(
    icon: ImageVector,
    label: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

/**
 * The board.
 *
 * Piece colours are relatives of the user's own accent rather than the genre's primaries, because
 * seven saturated hues dropped into a deliberately low-chroma app look like an advert has loaded.
 * They still have to be told apart at a glance, which is why they alternate light and deep.
 */
@Composable
private fun BlockBoard(cells: List<Int>, dimmed: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme.primary, scheme.tertiary, scheme.secondary) {
        listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary.lightened(0.28f),
            scheme.primary.deepened(0.28f),
            scheme.tertiary.lightened(0.32f),
            scheme.secondary,
            scheme.primary.lightened(0.34f),
        )
    }
    val empty = scheme.surfaceVariant
    val boardDescription = stringResource(R.string.blocks_board)

    // Sized from its height, not its width. A 8x14 board drawn the full width of a phone stands
    // nearly two screens tall and pushes its own controls below the fold, which is a strange way
    // to hand someone a game.
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = modifier
                .height(BLOCKS_BOARD_HEIGHT)
                .aspectRatio(BlockPuzzle.WIDTH.toFloat() / BlockPuzzle.HEIGHT.toFloat())
                .semantics { contentDescription = boardDescription },
        ) {
            val cell = size.width / BlockPuzzle.WIDTH
            val gap = cell * 0.08f
            val radius = CornerRadius(cell * 0.22f, cell * 0.22f)
            for (index in cells.indices) {
                val value = cells[index]
                val column = index % BlockPuzzle.WIDTH
                val row = index / BlockPuzzle.WIDTH
                val colour = if (value == 0) {
                    empty.copy(alpha = 0.5f)
                } else {
                    palette[(value - 1) % palette.size].copy(alpha = if (dimmed) 0.35f else 1f)
                }
                drawRoundRect(
                    color = colour,
                    topLeft = Offset(column * cell + gap, row * cell + gap),
                    size = Size(cell - gap * 2, cell - gap * 2),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** One entry on the shelf. Ids are also what a completed session is filed under. */
internal data class ToolEntry(
    val id: String,
    val title: Int,
    val summary: Int,
    val minutes: Int,
    val icon: ImageVector,
)

/**
 * Everything the toolkit can open.
 *
 * Split into what works *on* the craving and what simply takes the time, because those are two
 * different requests and a single undifferentiated list of buttons made the user choose between
 * them blind.
 */
internal object ToolCatalogue {
    const val BREATHING = "BREATHING"
    const val URGE_SURF = "URGE_SURF"
    const val BLOCKS = "BLOCKS"
    const val GROUNDING = "GROUNDING"
    const val CHANGE_PLACE = "CHANGE_PLACE"
    const val SEQUENCE = "SEQUENCE"
    const val MEMORY = "MEMORY"

    val guided = listOf(
        ToolEntry(BREATHING, R.string.breathing_title, R.string.breathing_summary, 2, Icons.Outlined.Air),
        ToolEntry(URGE_SURF, R.string.urge_surf_title, R.string.urge_surf_summary, 3, Icons.Outlined.Waves),
        ToolEntry(GROUNDING, R.string.grounding_title, R.string.grounding_summary, 2, Icons.Outlined.Spa),
        ToolEntry(CHANGE_PLACE, R.string.change_place_title, R.string.change_place_summary, 2, Icons.AutoMirrored.Outlined.DirectionsWalk),
    )

    val games = listOf(
        ToolEntry(BLOCKS, R.string.blocks_title, R.string.blocks_summary, 3, Icons.Outlined.GridView),
        ToolEntry(MEMORY, R.string.memory_title, R.string.memory_summary, 2, Icons.Outlined.Style),
        ToolEntry(SEQUENCE, R.string.sequence_title, R.string.sequence_summary, 1, Icons.Outlined.Tag),
    )
}

/** A titled group of tools, each row saying what it is and how long it asks for. */
@Composable
internal fun ToolShelf(
    title: String,
    body: String,
    entries: List<ToolEntry>,
    onOpen: (String) -> Unit,
) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        entries.forEach { entry -> ToolRow(entry) { onOpen(entry.id) } }
    }
}

/**
 * One tool, as a row rather than another identical outlined button.
 *
 * The old list gave four tools the same rectangle and the same label weight, so choosing between
 * them meant reading all four. An icon, a sentence and a duration let the choice happen in a
 * glance, which is about as much attention as a craving leaves spare.
 */
@Composable
private fun ToolRow(entry: ToolEntry, onClick: () -> Unit) {
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
                    entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(entry.title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(entry.summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.tool_duration_minutes, entry.minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The frame every guided tool sits in.
 *
 * The evidence line is not filler. These tools ask someone mid-craving to do something that feels
 * beside the point, and one sentence saying why it is not is the difference between following the
 * instruction and closing the card.
 */
@Composable
internal fun ToolShell(
    title: String,
    evidence: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close_tool)) }
        }
        Text(
            evidence,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

private fun breathPatternLabel(id: String): Int = when (id) {
    BreathPattern.RELAXING.id -> R.string.breathing_pattern_relaxing
    BreathPattern.COHERENT.id -> R.string.breathing_pattern_coherent
    else -> R.string.breathing_pattern_box
}

private fun breathPhaseLabel(phase: BreathPhase): Int = when (phase) {
    BreathPhase.INHALE -> R.string.breathing_phase_inhale
    BreathPhase.HOLD_IN -> R.string.breathing_phase_hold
    BreathPhase.EXHALE -> R.string.breathing_phase_exhale
    BreathPhase.HOLD_OUT -> R.string.breathing_phase_rest
}

/** Long enough to matter, short enough to agree to when you do not want to. */
private const val BREATHING_TARGET_SECONDS = 120

private const val BLOCKS_SESSION_MS = 3 * 60 * 1_000L

/** Forgiving on purpose: the task is occupation, not reflexes. */
private const val BLOCKS_GRAVITY_MS = 700L

/** Tall enough to play, short enough that the board and its controls share one screen. */
private val BLOCKS_BOARD_HEIGHT = 360.dp
