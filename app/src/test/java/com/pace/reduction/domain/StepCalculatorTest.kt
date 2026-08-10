package com.pace.reduction.domain

import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepCalculatorTest {

    private val today = LocalDate.of(2026, 8, 10)

    private fun day(daysAgo: Long, steps: Long) = StepDay(
        date = today.minusDays(daysAgo),
        steps = steps,
        distanceKm = StepCalculator.distanceKm(steps, 170),
    )

    @Test
    fun `first ever reading establishes a baseline without crediting steps`() {
        // The counter has been running since boot; those steps were not taken just now.
        assertEquals(0L, StepCalculator.deltaFor(previousAnchor = null, raw = 48_000))
    }

    @Test
    fun `normal reading credits only the difference`() {
        assertEquals(320L, StepCalculator.deltaFor(previousAnchor = 1_200, raw = 1_520))
    }

    @Test
    fun `standing still credits nothing`() {
        assertEquals(0L, StepCalculator.deltaFor(previousAnchor = 1_520, raw = 1_520))
    }

    @Test
    fun `a reading below the anchor is a reboot and counts from zero`() {
        // After a restart the sensor starts again at 0, so 140 means 140 steps since the restart —
        // not a negative delta, and not 140 minus the pre-reboot total.
        assertEquals(140L, StepCalculator.deltaFor(previousAnchor = 90_000, raw = 140))
    }

    @Test
    fun `a negative reading is ignored rather than trusted`() {
        assertEquals(0L, StepCalculator.deltaFor(previousAnchor = 100, raw = -5))
    }

    @Test
    fun `stride falls back to the default for implausible heights`() {
        val fallback = StepCalculator.strideMetres(StepCalculator.DEFAULT_HEIGHT_CM)
        assertEquals(fallback, StepCalculator.strideMetres(0), 1e-9)
        assertEquals(fallback, StepCalculator.strideMetres(40), 1e-9)
        assertEquals(fallback, StepCalculator.strideMetres(400), 1e-9)
    }

    @Test
    fun `distance scales with height`() {
        val short = StepCalculator.distanceKm(10_000, 150)
        val tall = StepCalculator.distanceKm(10_000, 190)
        assertTrue("a taller stride must cover more ground", tall > short)
        // 10k steps at 170cm is a shade over 7km, which is the familiar figure.
        assertTrue(abs(StepCalculator.distanceKm(10_000, 170) - 7.038) < 0.01)
    }

    @Test
    fun `averages ignore days with no recorded steps`() {
        // Two days walked, five days the phone stayed home. Dividing by seven would report a drop
        // in walking that never happened.
        val metrics = StepCalculator.calculate(
            today = today,
            days = listOf(day(1, 6_000), day(0, 4_000)),
            available = true,
            permissionGranted = true,
            enabled = true,
        )
        assertEquals(5_000.0, metrics.sevenDayAverage, 1e-9)
    }

    @Test
    fun `averages only count days inside the window`() {
        val metrics = StepCalculator.calculate(
            today = today,
            days = listOf(day(40, 20_000), day(0, 2_000)),
            available = true,
            permissionGranted = true,
            enabled = true,
        )
        assertEquals(2_000.0, metrics.sevenDayAverage, 1e-9)
        assertEquals(2_000.0, metrics.thirtyDayAverage, 1e-9)
        // Totals are lifetime, so the old day still shows up there.
        assertEquals(22_000L, metrics.totalSteps)
    }

    @Test
    fun `today best and totals are picked out`() {
        val metrics = StepCalculator.calculate(
            today = today,
            days = listOf(day(2, 9_500), day(1, 1_000), day(0, 3_200)),
            available = true,
            permissionGranted = true,
            enabled = true,
        )
        assertEquals(3_200L, metrics.todaySteps)
        assertEquals(9_500L, metrics.bestDay?.steps)
        assertEquals(13_700L, metrics.totalSteps)
        assertTrue(metrics.hasData)
    }

    @Test
    fun `an empty history reports nothing rather than zeroes that look like a bad day`() {
        val metrics = StepCalculator.calculate(
            today = today,
            days = emptyList(),
            available = true,
            permissionGranted = false,
            enabled = false,
        )
        assertEquals(0L, metrics.todaySteps)
        assertEquals(0.0, metrics.sevenDayAverage, 1e-9)
        assertNull(metrics.bestDay)
        assertTrue(!metrics.hasData)
    }
}
