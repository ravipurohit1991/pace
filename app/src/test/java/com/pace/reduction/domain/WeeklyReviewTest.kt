package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.DailyPlanSnapshot
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    /** A Wednesday: three days into the week, which is the case the comparison has to get right. */
    private val today: LocalDate = LocalDate.of(2026, 8, 12)
    private val thisMonday: LocalDate = LocalDate.of(2026, 8, 10)
    private val lastMonday: LocalDate = LocalDate.of(2026, 8, 3)

    private var sequence = 0

    private fun logs(date: LocalDate, count: Int, startHour: Int = 8): List<CigaretteLog> =
        (0 until count).map { index ->
            val moment = date.atTime(startHour + index, 0).atZone(zone).toInstant()
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

    private fun review(
        logs: List<CigaretteLog>,
        snapshots: List<DailyPlanSnapshot>,
        pricePerPack: Double = 0.0,
        on: LocalDate = today,
    ) = WeeklyReviewCalculator.calculate(
        today = on,
        zoneId = zone,
        logs = logs,
        snapshots = snapshots,
        pricePerPack = pricePerPack,
        cigarettesPerPack = 20,
    )

    @Test
    fun weighsThisWeekAgainstTheSameNumberOfDaysLastWeek() {
        // Three days this week, and a full seven last week — only the first three of which count.
        val thisWeek = (0L..2L).flatMap { logs(thisMonday.plusDays(it), 4) }
        val lastWeek = (0L..6L).flatMap { logs(lastMonday.plusDays(it), 6) }
        val snapshots = ((0L..2L).map { thisMonday.plusDays(it) } + (0L..6L).map { lastMonday.plusDays(it) })
            .map { snapshot(it) }

        val review = review(thisWeek + lastWeek, snapshots)

        val lastSummary = requireNotNull(review.lastWeek)
        assertEquals(12, review.thisWeek.logged)
        assertEquals(18, lastSummary.logged)
        assertEquals(3, review.thisWeek.daysCounted)
        assertEquals(3, lastSummary.daysCounted)
        assertEquals(-6, review.loggedDelta)
        assertTrue(review.partial)
    }

    @Test
    fun aFinishedWeekComparesAllSevenDays() {
        val sunday = LocalDate.of(2026, 8, 16)
        val review = review(
            logs = emptyList(),
            snapshots = listOf(snapshot(sunday)),
            on = sunday,
        )

        assertEquals(7, review.thisWeek.daysCounted)
        assertEquals(7, review.lastWeek?.daysCounted)
        assertFalse(review.partial)
    }

    @Test
    fun clearAndSteadyDaysOnlyCountDaysTheAppWasWatching() {
        val snapshots = (0L..2L).map { snapshot(thisMonday.plusDays(it), ceiling = 5) }
        // Monday clear, Tuesday steady, Wednesday over.
        val logged = logs(thisMonday.plusDays(1), 5) + logs(thisMonday.plusDays(2), 9)

        val review = review(logged, snapshots)

        assertEquals(3, review.thisWeek.recordedDays)
        assertEquals(1, review.thisWeek.clearDays)
        assertEquals(2, review.thisWeek.steadyDays)
        // Last week was never watched, so it has nothing to say.
        assertEquals(0, review.lastWeek?.recordedDays)
        assertFalse(review.comparable)
    }

    @Test
    fun longestGapBelongsToTheWeekTheWaitEndedIn() {
        // One log late last week and the next one on Monday: the wait was served this week.
        val before = logs(lastMonday.plusDays(6), 1, startHour = 20)
        val after = logs(thisMonday, 1, startHour = 8)

        val review = review(before + after, listOf(snapshot(thisMonday)))

        assertEquals(12 * 60L, review.thisWeek.longestGapMinutes)
        assertEquals(0L, review.lastWeek?.longestGapMinutes)
    }

    @Test
    fun moneyIsWhatTheBaselineWouldHaveCost() {
        val snapshots = (0L..2L).map { snapshot(thisMonday.plusDays(it), baseline = 20) }
        val logged = (0L..2L).flatMap { logs(thisMonday.plusDays(it), 10) }

        // Ten avoided a day for three days, at 10.00 for twenty in a pack.
        val review = review(logged, snapshots, pricePerPack = 10.0)

        assertEquals(0, review.thisWeek.moneySaved.compareTo(java.math.BigDecimal("15.00")))
    }

    @Test
    fun reversedLogsDoNotCount() {
        val kept = logs(thisMonday, 2)
        val undone = logs(thisMonday.plusDays(1), 3).map {
            it.copy(reversedAt = it.occurredAt.plusSeconds(5))
        }

        val review = review(kept + undone, (0L..1L).map { snapshot(thisMonday.plusDays(it)) })

        assertEquals(2, review.thisWeek.logged)
        assertEquals(1, review.thisWeek.clearDays)
    }
}
