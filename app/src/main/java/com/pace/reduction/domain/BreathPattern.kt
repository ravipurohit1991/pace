package com.pace.reduction.domain

/**
 * Paced breathing, expressed as data so the animation is a pure function of elapsed time.
 *
 * Slow-paced breathing is one of the few things with acute, single-session evidence behind it for
 * craving relief — which is why it is worth building properly rather than shipping a generic
 * "breathe" animation. Keeping the schedule out of Compose means the pacer cannot drift: it is
 * always derived from one elapsed-milliseconds number, so a recomposition, a rotation, or the
 * screen being left and returned to all land on the same breath.
 */
enum class BreathPhase { INHALE, HOLD_IN, EXHALE, HOLD_OUT }

data class BreathPattern(
    val id: String,
    val inhaleSeconds: Int,
    val holdInSeconds: Int,
    val exhaleSeconds: Int,
    val holdOutSeconds: Int,
) {
    val cycleSeconds: Int = inhaleSeconds + holdInSeconds + exhaleSeconds + holdOutSeconds

    init {
        require(cycleSeconds > 0) { "A breath pattern needs a non-zero cycle" }
    }

    /** Cycles that fill roughly [targetSeconds], never fewer than three. */
    fun cyclesFor(targetSeconds: Int): Int =
        ((targetSeconds + cycleSeconds - 1) / cycleSeconds).coerceAtLeast(3)

    companion object {
        /** Navy box breathing: even on all four sides, the easiest to hold when rattled. */
        val BOX = BreathPattern("BOX", 4, 4, 4, 4)

        /** 4-7-8: a long exhale, which is the half that does the calming. */
        val RELAXING = BreathPattern("RELAXING", 4, 7, 8, 0)

        /** Resonant/coherent breathing at six breaths a minute. */
        val COHERENT = BreathPattern("COHERENT", 6, 0, 6, 0)

        val all = listOf(BOX, RELAXING, COHERENT)

        fun byId(id: String): BreathPattern = all.firstOrNull { it.id == id } ?: BOX
    }
}

/**
 * Where the breath is at [elapsedMillis].
 *
 * [scale] is the only thing the animation needs: it runs 0..1 with the lungs, so the orb grows on
 * the inhale, sits still through a hold, and shrinks on the exhale without the drawing code having
 * to know which phase is which.
 */
data class BreathTick(
    val phase: BreathPhase,
    val secondsLeftInPhase: Int,
    val scale: Float,
    val completedCycles: Int,
)

fun BreathPattern.tickAt(elapsedMillis: Long): BreathTick {
    val safeElapsed = elapsedMillis.coerceAtLeast(0L)
    val cycleMillis = cycleSeconds * 1_000L
    val completed = (safeElapsed / cycleMillis).toInt()
    val intoCycle = (safeElapsed % cycleMillis).toFloat() / 1_000f

    var offset = intoCycle
    val phases = listOf(
        BreathPhase.INHALE to inhaleSeconds,
        BreathPhase.HOLD_IN to holdInSeconds,
        BreathPhase.EXHALE to exhaleSeconds,
        BreathPhase.HOLD_OUT to holdOutSeconds,
    )
    for ((phase, seconds) in phases) {
        if (seconds == 0) continue
        if (offset < seconds) {
            val progress = offset / seconds
            return BreathTick(
                phase = phase,
                // Rounded up, so a four-second phase reads "4" for the whole of its first second.
                secondsLeftInPhase = kotlin.math.ceil(seconds - offset).toInt().coerceAtLeast(1),
                scale = when (phase) {
                    BreathPhase.INHALE -> progress
                    BreathPhase.HOLD_IN -> 1f
                    BreathPhase.EXHALE -> 1f - progress
                    BreathPhase.HOLD_OUT -> 0f
                },
                completedCycles = completed,
            )
        }
        offset -= seconds
    }
    // Only reachable through floating-point slack at the very end of a cycle.
    return BreathTick(BreathPhase.INHALE, inhaleSeconds, 0f, completed)
}
