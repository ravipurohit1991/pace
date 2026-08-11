package com.pace.reduction.domain

import java.time.Duration

/**
 * One stage of what the body does after the last one, keyed to hours since it.
 *
 * The published cessation guidance is consistent on the shape: symptoms begin within about four
 * hours, are at their sharpest across the second and third day, and fade over three to four weeks.
 */
data class WithdrawalPhase(
    val id: String,
    val fromHours: Long,
    val toHours: Long,
    /** Roughly how loud this stage is, 0..1. Drives the curve, not a claim about anyone's day. */
    val intensity: Float,
    val reached: Boolean,
    val current: Boolean,
)

data class WithdrawalStatus(
    val hoursIn: Long,
    val phases: List<WithdrawalPhase>,
    val current: WithdrawalPhase?,
    /** Hours until the sharpest stretch is behind them, or 0 once it is. */
    val hoursToPeakEnd: Long,
    val peakPassed: Boolean,
    /**
     * False once the whole curve is behind them. The card retires itself rather than sitting on
     * Progress forever announcing that nothing is happening.
     */
    val relevant: Boolean,
)

/**
 * The counterpart to [QuitProgress]'s recovery ladder.
 *
 * Recovery says what is being won and stretches out to ten years; this says what the next few days
 * cost and then gets out of the way. Knowing the summit is day two to three *before* standing on it
 * is the protective part — a person who thinks it climbs forever has no reason to wait.
 */
object WithdrawalTimeline {
    /** Hours drawn on the curve: the onset, the peak and the turn all fall inside the first week. */
    const val CURVE_HOURS = 7 * 24L

    /** Past this the ladder is finished and the card stops being shown. */
    const val RELEVANT_HOURS = 30 * 24L

    private const val PEAK_END_HOURS = 72L

    private val PHASES = listOf(
        Triple("settling", 0L to 4L, 0.25f),
        Triple("climbing", 4L to 24L, 0.60f),
        Triple("peak", 24L to PEAK_END_HOURS, 1.00f),
        Triple("turning", PEAK_END_HOURS to 7 * 24L, 0.68f),
        Triple("easing", 7 * 24L to 14 * 24L, 0.42f),
        Triple("fading", 14 * 24L to RELEVANT_HOURS, 0.20f),
        Triple("clear", RELEVANT_HOURS to Long.MAX_VALUE, 0.05f),
    )

    fun calculate(smokeFree: Duration): WithdrawalStatus {
        val hours = smokeFree.toHours().coerceAtLeast(0)
        val phases = PHASES.map { (id, bounds, intensity) ->
            val (from, to) = bounds
            WithdrawalPhase(
                id = id,
                fromHours = from,
                toHours = to,
                intensity = intensity,
                reached = hours >= from,
                current = hours >= from && hours < to,
            )
        }
        return WithdrawalStatus(
            hoursIn = hours,
            phases = phases,
            current = phases.firstOrNull { it.current },
            hoursToPeakEnd = (PEAK_END_HOURS - hours).coerceAtLeast(0),
            peakPassed = hours >= PEAK_END_HOURS,
            relevant = hours < RELEVANT_HOURS,
        )
    }

    /**
     * The curve to draw, sampled evenly across [CURVE_HOURS].
     *
     * Interpolated between the midpoints of the phases rather than stepped, because a staircase
     * would read as seven separate claims about seven separate days when the real shape is one hill.
     */
    fun curve(samples: Int = 48): List<Float> {
        require(samples > 1) { "A curve needs at least two samples" }
        return (0 until samples).map { index ->
            intensityAt(index.toFloat() / (samples - 1) * CURVE_HOURS)
        }
    }

    /** Where on the hill a given number of hours falls, 0..1. */
    fun intensityAt(hours: Float): Float {
        val anchors = PHASES
            .filter { it.second.first < CURVE_HOURS }
            .map { (_, bounds, intensity) ->
                val (from, to) = bounds
                val mid = (from + minOf(to, CURVE_HOURS)) / 2f
                mid to intensity
            }
        val first = anchors.first()
        val last = anchors.last()
        if (hours <= first.first) {
            // Rise out of nothing rather than starting mid-slope: hour zero is the last one, and
            // nobody is in withdrawal while they are still holding it.
            return first.second * (hours / first.first).coerceIn(0f, 1f)
        }
        if (hours >= last.first) return last.second
        val upper = anchors.indexOfFirst { it.first >= hours }
        val (leftHours, leftValue) = anchors[upper - 1]
        val (rightHours, rightValue) = anchors[upper]
        val t = (hours - leftHours) / (rightHours - leftHours)
        return leftValue + (rightValue - leftValue) * t
    }
}
