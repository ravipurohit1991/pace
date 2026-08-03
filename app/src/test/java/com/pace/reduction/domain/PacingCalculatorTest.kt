package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.PacingStatus
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.QuietSchedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacingCalculatorTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")

    @Test
    fun quietHoursCrossMidnight() {
        val schedule = QuietSchedule(LocalTime.of(7, 0), LocalTime.of(22, 30))

        assertTrue(PacingCalculator.isQuiet(LocalTime.of(23, 0), schedule))
        assertTrue(PacingCalculator.isQuiet(LocalTime.of(6, 59), schedule))
        assertFalse(PacingCalculator.isQuiet(LocalTime.of(12, 0), schedule))
    }

    @Test
    fun sameDayQuietInterval() {
        val schedule = QuietSchedule(LocalTime.of(10, 0), LocalTime.of(2, 0))

        assertTrue(PacingCalculator.isQuiet(LocalTime.of(5, 0), schedule))
        assertFalse(PacingCalculator.isQuiet(LocalTime.of(23, 0), schedule))
    }

    @Test
    fun gapAndMorningHoldUseLaterBoundary() {
        val now = Instant.parse("2026-08-03T06:00:00Z") // 08:00 Copenhagen
        val last = Instant.parse("2026-08-03T05:30:00Z") // 07:30 Copenhagen
        val settings = PlanSettings(
            dailyCeiling = 12,
            minimumGapMinutes = 120,
            wakeMinutes = 7 * 60,
            sleepMinutes = 22 * 60,
            morningHoldMinutes = 30,
        )

        val result = PacingCalculator.calculate(
            now = now,
            zoneId = copenhagen,
            settings = settings,
            logs = listOf(
                CigaretteLog("1", last, last, "APP", null),
            ),
        )

        val spacing = result.status as PacingStatus.Spacing
        assertEquals("2026-08-03T09:30+02:00[Europe/Copenhagen]", spacing.earliestWindow.toString())
    }

    @Test
    fun recoveryAllowsHonestCountAboveCeiling() {
        val now = Instant.parse("2026-08-03T10:00:00Z")
        val logs = (1..3).map { index ->
            val time = now.minusSeconds(index * 600L)
            CigaretteLog(index.toString(), time, time, "APP", null)
        }

        val result = PacingCalculator.calculate(
            now = now,
            zoneId = copenhagen,
            settings = PlanSettings(dailyCeiling = 2),
            logs = logs,
        )

        assertEquals(3, result.count)
        assertTrue(result.status is PacingStatus.Recovery)
    }

    @Test
    fun reversedLogsDoNotCount() {
        val now = Instant.parse("2026-08-03T10:00:00Z")
        val logs = listOf(
            CigaretteLog("active", now.minusSeconds(600), now, "APP", null),
            CigaretteLog("reversed", now.minusSeconds(300), now, "APP", now),
        )

        val result = PacingCalculator.calculate(now, copenhagen, PlanSettings(), logs)

        assertEquals(1, result.count)
    }

    @Test
    fun weekendWakeOverrideControlsMorningHold() {
        val settings = PlanSettings(
            weekendWakeEnabled = true,
            weekendWakeMinutes = 8 * 60,
            wakeMinutes = 7 * 60,
            sleepMinutes = 22 * 60 + 30,
            morningHoldMinutes = 30,
        )
        val now = Instant.parse("2026-08-02T06:10:00Z") // Sunday 08:10 local

        val result = PacingCalculator.calculate(now, copenhagen, settings, emptyList())

        val hold = result.status as PacingStatus.MorningHold
        assertEquals("2026-08-02T08:30+02:00[Europe/Copenhagen]", hold.until.toString())
    }

    @Test
    fun earliestWindowRollingIntoQuietHoursMovesToNextWakeAndHold() {
        val now = Instant.parse("2026-08-03T20:00:00Z") // 22:00 local
        val last = Instant.parse("2026-08-03T19:30:00Z") // 21:30 local
        val settings = PlanSettings(
            minimumGapMinutes = 120,
            wakeMinutes = 7 * 60,
            sleepMinutes = 22 * 60 + 30,
            morningHoldMinutes = 30,
        )

        val result = PacingCalculator.calculate(now, copenhagen, settings, listOf(CigaretteLog("1", last, last, "APP", null)))

        val spacing = result.status as PacingStatus.Spacing
        assertEquals("2026-08-04T07:30+02:00[Europe/Copenhagen]", spacing.earliestWindow.toString())
    }

    @Test
    fun dstSpringForwardMorningHoldUsesRealZonedTime() {
        val now = Instant.parse("2026-03-29T01:10:00Z") // 03:10 after the clock jump
        val settings = PlanSettings(wakeMinutes = 3 * 60, sleepMinutes = 23 * 60, morningHoldMinutes = 30)

        val result = PacingCalculator.calculate(now, copenhagen, settings, emptyList())

        val hold = result.status as PacingStatus.MorningHold
        assertEquals(20, PacingCalculator.remaining(now, hold.until).toMinutes())
    }

    @Test
    fun dstFallBackGapUsesElapsedInstantNotWallClockDifference() {
        val last = Instant.parse("2026-10-25T00:30:00Z") // 02:30 summer time
        val now = Instant.parse("2026-10-25T01:15:00Z") // 02:15 winter time
        val settings = PlanSettings(minimumGapMinutes = 60, wakeMinutes = 0, sleepMinutes = 23 * 60)

        val result = PacingCalculator.calculate(now, copenhagen, settings, listOf(CigaretteLog("1", last, last, "APP", null)))

        val spacing = result.status as PacingStatus.Spacing
        assertEquals(15, PacingCalculator.remaining(now, spacing.earliestWindow).toMinutes())
    }

    @Test
    fun zeroCeilingIsValidAndFirstLogEntersRecovery() {
        val now = Instant.parse("2026-08-03T10:00:00Z")
        val noLogs = PacingCalculator.calculate(now, copenhagen, PlanSettings(dailyCeiling = 0), emptyList())
        val oneLog = PacingCalculator.calculate(
            now,
            copenhagen,
            PlanSettings(dailyCeiling = 0),
            listOf(CigaretteLog("1", now, now, "APP", null)),
        )

        assertTrue(noLogs.status is PacingStatus.CeilingReached)
        assertTrue(oneLog.status is PacingStatus.Recovery)
    }
}
