package com.pace.reduction.domain

import com.pace.reduction.domain.model.PlanSettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSpacingTest {
    private val plan = PlanSettings(
        minimumGapMinutes = 120,
        adaptiveSpacingEnabled = true,
        adaptiveSpacingStepMinutes = 15,
        adaptiveSpacingIntervalDays = 7,
        adaptiveSpacingMaxMinutes = 240,
    )

    /** [steady] completed days, then today (always an unknown verdict). */
    private fun days(steady: Int, unsteady: Int = 0): List<DailyProgress> {
        val start = LocalDate.of(2026, 1, 1)
        var index = 0
        return buildList {
            repeat(steady) { add(day(start.plusDays(index++.toLong()), true)) }
            repeat(unsteady) { add(day(start.plusDays(index++.toLong()), false)) }
            add(day(start.plusDays(index.toLong()), null))
        }
    }

    private fun day(date: LocalDate, steady: Boolean?) = DailyProgress(
        date = date,
        count = 0,
        ceiling = 10,
        recorded = steady != null,
        completed = steady != null,
        steady = steady,
    )

    @Test
    fun disabledPlanKeepsTheBaseGap() {
        val settings = plan.copy(adaptiveSpacingEnabled = false)

        val progress = AdaptiveSpacing.progress(settings, days(steady = 30))

        assertEquals(120, progress.effectiveMinutes)
        assertEquals(settings, AdaptiveSpacing.applyTo(settings, days(steady = 30)))
    }

    @Test
    fun gapHoldsUntilAFullIntervalOfSteadyDays() {
        val progress = AdaptiveSpacing.progress(plan, days(steady = 6))

        assertEquals(120, progress.effectiveMinutes)
        assertEquals(6, progress.steadyDays)
        assertEquals(7, progress.steadyDaysNeeded)
    }

    @Test
    fun eachIntervalOfSteadyDaysAddsOneStep() {
        assertEquals(135, AdaptiveSpacing.progress(plan, days(steady = 7)).effectiveMinutes)
        assertEquals(135, AdaptiveSpacing.progress(plan, days(steady = 13)).effectiveMinutes)
        assertEquals(150, AdaptiveSpacing.progress(plan, days(steady = 14)).effectiveMinutes)
    }

    @Test
    fun unsteadyDaysEarnNothingButNeverReduceTheGap() {
        val earned = AdaptiveSpacing.progress(plan, days(steady = 7)).effectiveMinutes
        val withRelapse = AdaptiveSpacing.progress(plan, days(steady = 7, unsteady = 5)).effectiveMinutes

        assertEquals(135, earned)
        assertEquals("a bad day must not push the target further away", earned, withRelapse)
    }

    @Test
    fun todayNeverCountsBeforeItCloses() {
        // 7 steady + today unknown: today must not be credited as an eighth.
        val progress = AdaptiveSpacing.progress(plan, days(steady = 7))

        assertEquals(0, progress.steadyDays)
        assertFalse(progress.atMaximum)
    }

    @Test
    fun gapStopsAtTheConfiguredMaximum() {
        val progress = AdaptiveSpacing.progress(plan, days(steady = 700))

        assertEquals(240, progress.effectiveMinutes)
        assertTrue(progress.atMaximum)
    }

    @Test
    fun applyToRewritesOnlyTheMinimumGap() {
        val applied = AdaptiveSpacing.applyTo(plan, days(steady = 14))

        assertEquals(150, applied.minimumGapMinutes)
        assertEquals(plan.copy(minimumGapMinutes = 150), applied)
    }
}
