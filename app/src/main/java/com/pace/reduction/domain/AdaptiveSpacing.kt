package com.pace.reduction.domain

import com.pace.reduction.domain.model.PlanSettings

data class SpacingProgress(
    val baseMinutes: Int,
    val effectiveMinutes: Int,
    /** Steady days counted so far toward the next increase. */
    val steadyDays: Int,
    val steadyDaysNeeded: Int,
    val atMaximum: Boolean,
) {
    val stepsEarned: Int get() = if (baseMinutes == 0) 0 else effectiveMinutes - baseMinutes
}

/**
 * Grows the minimum gap as evidence accumulates, so reduction happens without the user having to
 * keep tightening the plan by hand.
 *
 * Increases are earned by *steady* days — days finished at or below the ceiling — not by the
 * calendar. A difficult day simply doesn't earn credit; it never pushes the target further away.
 */
object AdaptiveSpacing {
    fun progress(settings: PlanSettings, days: List<DailyProgress>): SpacingProgress {
        val base = settings.minimumGapMinutes
        if (!settings.adaptiveSpacingEnabled) {
            return SpacingProgress(base, base, 0, settings.adaptiveSpacingIntervalDays, atMaximum = false)
        }

        val interval = settings.adaptiveSpacingIntervalDays.coerceAtLeast(1)
        val step = settings.adaptiveSpacingStepMinutes.coerceAtLeast(0)
        val maximum = settings.adaptiveSpacingMaxMinutes.coerceAtLeast(base)

        // Only completed days carry a steady verdict; today's is null until it closes.
        val steadyDays = days.count { it.steady == true }
        val effective = (base + step * (steadyDays / interval)).coerceAtMost(maximum)

        return SpacingProgress(
            baseMinutes = base,
            effectiveMinutes = effective,
            steadyDays = steadyDays % interval,
            steadyDaysNeeded = interval,
            atMaximum = effective >= maximum,
        )
    }

    /** The plan the pacing calculator should actually run with. */
    fun applyTo(settings: PlanSettings, days: List<DailyProgress>): PlanSettings =
        if (!settings.adaptiveSpacingEnabled) {
            settings
        } else {
            settings.copy(minimumGapMinutes = progress(settings, days).effectiveMinutes)
        }
}
