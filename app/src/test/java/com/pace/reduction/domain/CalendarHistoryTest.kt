package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.DailyPlanSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarHistoryTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    /** A Wednesday, so the current week is deliberately half-finished. */
    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    private var sequence = 0

    private fun logs(date: LocalDate, count: Int): List<CigaretteLog> = (0 until count).map { index ->
        val moment = date.atTime(9, index).atZone(zone).toInstant()
        CigaretteLog(
            id = "log-${sequence++}",
            occurredAt = moment,
            recordedAt = moment,
            source = "APP",
            reversedAt = null,
        )
    }

    private fun snapshot(date: LocalDate, ceiling: Int = 10, baseline: Int = 20) = DailyPlanSnapshot(
        localDate = date,
        baseline = baseline,
        ceiling = ceiling,
        minimumGapMinutes = 60,
        wakeMinutes = 420,
        morningHoldMinutes = 30,
    )

    private fun dayFor(history: CalendarHistory, date: LocalDate): CalendarDay =
        requireNotNull(history.weeks.flatMap { it.days }.filterNotNull().firstOrNull { it.date == date }) {
            "no cell for $date"
        }

    @Test
    fun everyRowIsAFullWeekStartingOnTheGivenDay() {
        val history = CalendarHistoryBuilder.build(
            today = today,
            zoneId = zone,
            logs = emptyList(),
            snapshots = emptyList(),
            weeks = 4,
        )

        assertEquals(4, history.weeks.size)
        history.weeks.forEach { week ->
            assertEquals(7, week.days.size)
            assertEquals(DayOfWeek.MONDAY, week.start.dayOfWeek)
        }
        // The last row is the week containing today, not the week ending today.
        assertEquals(LocalDate.of(2026, 8, 10), history.weeks.last().start)
    }

    @Test
    fun honoursASundayFirstLocale() {
        val history = CalendarHistoryBuilder.build(
            today = today,
            zoneId = zone,
            logs = emptyList(),
            snapshots = emptyList(),
            weeks = 2,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )

        history.weeks.forEach { assertEquals(DayOfWeek.SUNDAY, it.start.dayOfWeek) }
        assertEquals(LocalDate.of(2026, 8, 9), history.weeks.last().start)
    }

    @Test
    fun gradesEachDayAgainstTheCeilingItWasGiven() {
        val clear = today.minusDays(4)
        val under = today.minusDays(3)
        val at = today.minusDays(2)
        val over = today.minusDays(1)
        val history = CalendarHistoryBuilder.build(
            today = today,
            zoneId = zone,
            logs = logs(under, 4) + logs(at, 10) + logs(over, 14),
            snapshots = listOf(clear, under, at, over).map { snapshot(it) },
            weeks = 3,
        )

        assertEquals(DayStanding.CLEAR, dayFor(history, clear).standing)
        assertEquals(DayStanding.UNDER, dayFor(history, under).standing)
        assertEquals(DayStanding.AT, dayFor(history, at).standing)
        assertEquals(DayStanding.OVER, dayFor(history, over).standing)
    }

    @Test
    fun daysBeforeTheAppWasWatchingAreUnknownRatherThanPerfect() {
        val watched = today.minusDays(1)
        val history = CalendarHistoryBuilder.build(
            today = today,
            zoneId = zone,
            logs = emptyList(),
            snapshots = listOf(snapshot(watched)),
            weeks = 6,
        )

        assertEquals(DayStanding.CLEAR, dayFor(history, watched).standing)
        assertEquals(DayStanding.UNKNOWN, dayFor(history, today.minusDays(30)).standing)
        // Exactly one day counts, so an empty month cannot be read as a month of clear days.
        assertEquals(1, history.recordedDays)
        assertEquals(1, history.clearDays)
    }

    @Test
    fun aDayLoggedWithoutAPlanStillShowsUp() {
        val imported = today.minusDays(5)
        val history = CalendarHistoryBuilder.build(
            today = today,
            zoneId = zone,
            logs = logs(imported, 3),
            snapshots = emptyList(),
            weeks = 3,
        )

        val cell = dayFor(history, imported)
        assertEquals(3, cell.count)
        assertNull(cell.ceiling)
        assertEquals(DayStanding.AT, cell.standing)
        assertEquals(1, history.recordedDays)
    }

    @Test
    fun therestOfThisWeekIsHeldOpenRatherThanDropped() {
        val history = CalendarHistoryBuilder.build(
            today = today,
            zoneId = zone,
            logs = emptyList(),
            snapshots = listOf(snapshot(today)),
            weeks = 2,
        )

        val tomorrow = dayFor(history, today.plusDays(1))
        assertTrue(tomorrow.future)
        assertEquals(DayStanding.UNKNOWN, tomorrow.standing)
        assertFalse(dayFor(history, today).future)
        // A day that has not happened is not a day that went well.
        assertEquals(1, history.recordedDays)
    }

    @Test
    fun countsSteadyDaysAcrossTheWholeGrid() {
        val days = (1L..10L).map { today.minusDays(it) }
        val overspent = days.take(3)
        val history = CalendarHistoryBuilder.build(
            today = today,
            zoneId = zone,
            logs = overspent.flatMap { logs(it, 12) },
            snapshots = days.map { snapshot(it) },
            weeks = 4,
        )

        assertEquals(10, history.recordedDays)
        assertEquals(7, history.steadyDays)
        assertEquals(7, history.clearDays)
        assertFalse(history.isEmpty)
    }
}
