package com.pace.reduction.domain

import kotlin.math.PI
import kotlin.math.sin

/**
 * The shape of an urge over time, for the urge-surfing tool.
 *
 * Marlatt's point in mindfulness-based relapse prevention is that a craving is a wave: it builds,
 * crests, and falls of its own accord, usually well inside twenty minutes, and it does that whether
 * or not it is obeyed. The tool draws exactly that curve and asks the user to watch it rather than
 * fight it — so the curve has to be honest about the shape, which is a fast rise, an early crest,
 * and a long tail rather than a symmetrical hill.
 *
 * Three minutes is not the length of a real craving. It is long enough to feel the fall begin,
 * which is the part people do not believe until they have watched it once.
 */
object UrgeWave {
    const val DURATION_MS = 3 * 60 * 1_000L

    /** The crest sits early, because urges peak long before they are half over. */
    private const val CREST = 0.32f

    /** Where the tail settles: an urge that faded to nothing would be a lie. */
    private const val TAIL = 0.18f

    /**
     * Height of the wave at [progress] (0..1 through the session), itself 0..1.
     *
     * Smoothstepped on both sides so the crest is a curve rather than a corner, with a small
     * ripple over the top — an urge that recedes in a perfectly clean line does not look like
     * anything anyone has felt.
     */
    fun height(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val base = if (p <= CREST) {
            smoothstep(p / CREST)
        } else {
            val fallen = smoothstep((p - CREST) / (1f - CREST))
            1f - (1f - TAIL) * fallen
        }
        val ripple = 0.04f * sin(p * 6f * PI.toFloat()) * base
        return (base + ripple).coerceIn(0f, 1f)
    }

    /** Index into the six urge-surfing prompts, spread evenly across the session. */
    fun stageIndex(progress: Float, stages: Int = STAGE_COUNT): Int =
        (progress.coerceIn(0f, 1f) * stages).toInt().coerceIn(0, stages - 1)

    const val STAGE_COUNT = 6

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
