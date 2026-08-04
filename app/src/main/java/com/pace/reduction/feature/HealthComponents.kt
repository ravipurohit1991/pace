package com.pace.reduction.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.AnimatedCount
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.core.designsystem.pulseAlpha
import com.pace.reduction.domain.QuitMetrics
import com.pace.reduction.domain.RecoveryMilestone
import java.time.Duration

/**
 * The gauge the whole Today screen is built around.
 *
 * Two concentric arcs answer the two questions a person actually has, without making them read
 * anything: the outer one is how much of today's ceiling is spent, the inner one is how far
 * through the current wait they are. [windowProgress] is recomputed every second by the view
 * model's ticker, so the inner arc creeps forward while the screen is simply open — the one place
 * in the app where waiting is visibly doing something.
 */
@Composable
internal fun ProgressRing(
    count: Int,
    ceiling: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    windowProgress: Float? = null,
    glow: Boolean = false,
) {
    val motion = LocalMotion.current
    val fraction = if (ceiling > 0) (count.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = motion.springy(),
        label = "ring",
    )
    // The wait arc must not spring: it is a clock, and overshooting a clock reads as a glitch.
    val animatedWindow by animateFloatAsState(
        targetValue = windowProgress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = motion.eased(600),
        label = "windowRing",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val innerTrack = MaterialTheme.colorScheme.outlineVariant
    val over = ceiling > 0 && count > ceiling
    val arcColor = if (over) MaterialTheme.colorScheme.error else accent

    Box(modifier = modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
        // A halo rather than a hard edge: it reads as light coming off the ring, and because it
        // only appears when a window is open it doubles as the screen's "you may decide now" cue.
        if (glow) {
            Box(
                modifier = Modifier
                    .size(RING_SIZE)
                    .pulseAlpha(min = 0.18f, max = 0.5f, periodMillis = 2_800)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(accent, Color.Transparent),
                            radius = with(LocalDensity.current) { RING_SIZE.toPx() * 0.52f },
                        ),
                        shape = CircleShape,
                    ),
            )
        }
        Canvas(modifier = Modifier.size(RING_SIZE)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    // Sweeping the accent into a lighter relative of itself gives the arc a
                    // direction of travel, which a flat colour cannot.
                    brush = Brush.sweepGradient(
                        0f to arcColor.copy(alpha = 0.75f),
                        0.75f to arcColor,
                        1f to arcColor.copy(alpha = 0.75f),
                    ),
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            if (windowProgress != null) {
                val innerStroke = 5.dp.toPx()
                val innerInset = stroke + 7.dp.toPx()
                drawArc(
                    color = innerTrack,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP,
                    useCenter = false,
                    topLeft = Offset(innerInset, innerInset),
                    size = Size(size.width - innerInset * 2, size.height - innerInset * 2),
                    style = Stroke(width = innerStroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP * animatedWindow,
                    useCenter = false,
                    topLeft = Offset(innerInset, innerInset),
                    size = Size(size.width - innerInset * 2, size.height - innerInset * 2),
                    style = Stroke(width = innerStroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedCount(
                value = count,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                stringResource(R.string.plan_summary_ceiling, ceiling),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RING_SIZE = 178.dp

/** Open at the bottom, so the gap reads as a dial rather than a broken circle. */
private const val START_ANGLE = 135f
private const val SWEEP = 270f

/**
 * Compact figure + caption used in the stat rows.
 *
 * The value changes character by character rather than in one cut, which matters because these
 * tiles sit under a live clock: a figure that snaps looks like a redraw, one that crossfades looks
 * like it is being counted.
 */
@Composable
internal fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = value,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(motion.eased(260)) togetherWith
                        androidx.compose.animation.fadeOut(motion.eased(200))
                },
                label = "statValue",
            ) { shown ->
                Text(
                    shown,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The single upcoming body-recovery milestone, with progress toward it. */
@Composable
internal fun NextMilestoneCard(quit: QuitMetrics, modifier: Modifier = Modifier) {
    val next = quit.nextMilestone ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.quit_health_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(milestoneLabel(next.id), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { next.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.quit_next_milestone, milestoneWindow(next.afterMinutes)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Full recovery ladder shown on Progress. */
@Composable
internal fun RecoveryTimeline(quit: QuitMetrics) {
    val reachedColor = MaterialTheme.colorScheme.primary
    val pendingColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        quit.milestones.forEach { milestone ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp)) {
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(color = if (milestone.reached) reachedColor else pendingColor)
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp).fillMaxWidth()) {
                    Text(
                        milestoneLabel(milestone.id),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (milestone.reached) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (milestone.reached) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        milestoneWindow(milestone.afterMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun milestoneLabel(id: String): String = stringResource(
    when (id) {
        "heart_rate" -> R.string.milestone_heart_rate
        "carbon_monoxide" -> R.string.milestone_carbon_monoxide
        "heart_attack_risk" -> R.string.milestone_heart_attack_risk
        "smell_taste" -> R.string.milestone_smell_taste
        "nicotine_clear" -> R.string.milestone_nicotine_clear
        "circulation" -> R.string.milestone_circulation
        "lung_function" -> R.string.milestone_lung_function
        "breathing" -> R.string.milestone_breathing
        "cilia" -> R.string.milestone_cilia
        "heart_disease" -> R.string.milestone_heart_disease
        "stroke_risk" -> R.string.milestone_stroke_risk
        else -> R.string.milestone_lung_cancer
    },
)

internal fun milestoneWindow(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes < 24 * 60 -> "${minutes / 60} h"
    minutes < 30 * 24 * 60 -> plural(minutes / (24 * 60), "day")
    minutes < 365 * 24 * 60 -> plural(minutes / (30 * 24 * 60), "month")
    else -> plural(minutes / (365 * 24 * 60), "year")
}

private fun plural(value: Long, unit: String): String =
    if (value == 1L) "$value $unit" else "$value ${unit}s"

/** "3d 4h" / "4h 12m" / "12m" — compact enough for a stat tile. */
internal fun formatSmokeFree(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

/** Minutes of life regained, shown as the largest sensible unit. */
internal fun formatLifeRegained(minutes: Long): String = when {
    minutes < 60 -> "$minutes m"
    minutes < 24 * 60 -> "${minutes / 60} h"
    else -> "${minutes / (24 * 60)} d"
}
