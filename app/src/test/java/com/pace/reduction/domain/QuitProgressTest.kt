package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitProgressTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = LocalDate.of(2026, 8, 3).atTime(12, 0).atZone(zone).toInstant()

    private fun log(daysAgo: Long, hour: Int = 9, id: String = "$daysAgo-$hour") = CigaretteLog(
        id = id,
        occurredAt = LocalDate.of(2026, 8, 3).minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant(),
        recordedAt = now,
        source = "APP",
        reversedAt = null,
    )

    private fun calculate(
        logs: List<CigaretteLog>,
        baseline: Int = 10,
        pricePerPack: Double = 0.0,
        quitDate: LocalDate? = null,
    ) = QuitProgress.calculate(now, zone, logs, baseline, pricePerPack, 20, quitDate)

    @Test
    fun smokeFreeDurationMeasuresFromTheMostRecentCigarette() {
        // Latest log is Aug 2 12:00, "now" is Aug 3 12:00 — exactly one day, not three.
        val metrics = calculate(listOf(log(daysAgo = 3), log(daysAgo = 1, hour = 12)))

        assertEquals(1, metrics.smokeFreeDays)
        assertEquals(24, metrics.smokeFreeDuration.toHours())
    }

    @Test
    fun quitDateAnchorsTheRunWhenNothingIsLogged() {
        val metrics = calculate(logs = emptyList(), quitDate = LocalDate.of(2026, 8, 1))

        assertEquals(2, metrics.smokeFreeDays)
    }

    @Test
    fun reversedLogsDoNotBreakTheRun() {
        val reversed = log(daysAgo = 0, hour = 11).copy(reversedAt = now)
        val metrics = calculate(listOf(log(daysAgo = 4), reversed))

        assertEquals(4, metrics.smokeFreeDays)
    }

    @Test
    fun zeroDayStreakCountsWholeDaysSinceTheLastLoggedDay() {
        val metrics = calculate(listOf(log(daysAgo = 5), log(daysAgo = 3)))

        assertEquals(3, metrics.zeroDayStreak)
        assertTrue(metrics.bestZeroDayStreak >= metrics.zeroDayStreak)
    }

    @Test
    fun bestStreakSurvivesALaterRelapse() {
        // Clean days 6..3, one cigarette on day 2, then clean again.
        val metrics = calculate(listOf(log(daysAgo = 7), log(daysAgo = 2)))

        assertEquals(2, metrics.zeroDayStreak)
        assertEquals(4, metrics.bestZeroDayStreak)
    }

    @Test
    fun avoidedCigarettesAndSavingsFollowTheBaseline() {
        // Tracking starts at the first log's midnight (Jul 30) and runs to Aug 3 12:00 = 4.5 days.
        // Baseline 10/day = 45 expected, 2 actually smoked, so 43 avoided at 3.00 each.
        val metrics = calculate(listOf(log(daysAgo = 4, hour = 12), log(daysAgo = 2)), pricePerPack = 60.0)

        assertEquals(43, metrics.cigarettesAvoided)
        assertEquals(43 * QuitProgress.MINUTES_PER_CIGARETTE, metrics.minutesOfLifeRegained)
        assertEquals(43 * 3.0, metrics.moneySaved, 0.001)
    }

    @Test
    fun avoidedCigarettesNeverGoNegative() {
        val heavy = (0..9).map { log(daysAgo = 0, hour = it, id = "heavy-$it") }
        val metrics = calculate(heavy, baseline = 1)

        assertEquals(0, metrics.cigarettesAvoided)
        assertEquals(0.0, metrics.moneySaved, 0.0)
    }

    @Test
    fun milestonesUnlockInOrderAndExposeTheNextTarget() {
        val metrics = calculate(listOf(log(daysAgo = 1, hour = 12)))

        val reached = metrics.milestones.filter { it.reached }.map { it.id }
        assertTrue("carbon_monoxide" in reached)
        assertTrue("heart_attack_risk" in reached)
        assertFalse("smell_taste" in reached)
        assertEquals("smell_taste", metrics.nextMilestone?.id)
    }

    @Test
    fun everyMilestoneReachedLeavesNoNextTarget() {
        val metrics = calculate(logs = emptyList(), quitDate = LocalDate.of(2000, 1, 1))

        assertTrue(metrics.milestones.all { it.reached })
        assertNull(metrics.nextMilestone)
    }
}
