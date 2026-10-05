package com.pace.reduction.domain

import com.pace.reduction.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PaceInsightsTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private val today = LocalDate.of(2026, 9, 8)
    private val now = today.atTime(16, 0).atZone(zone).toInstant()
    private fun log(date: LocalDate, hour: Int, minute: Int = 0, reversed: Boolean = false): CigaretteLog {
        val instant = date.atTime(hour, minute).atZone(zone).toInstant()
        return CigaretteLog("$date-$hour-$minute", instant, instant, "TEST", if (reversed) now else null)
    }
    private fun plan(date: LocalDate, baseline: Int = 12, ceiling: Int = 8) = DailyPlanSnapshot(date, baseline, ceiling, 90, 420, 30)
    private fun report(logs: List<CigaretteLog> = emptyList(), plans: List<DailyPlanSnapshot> = emptyList(),
        sessions: List<UrgeSession> = emptyList(), drinks: List<BeverageLog> = emptyList(), days: Int = 7) =
        PaceInsightsCalculator.calculate(now, zone, logs, drinks, sessions, plans, days)

    @Test fun `empty history is unknown not a week of zeroes`() {
        val result = report()
        assertNull(result.average)
        assertNull(result.changePercent)
        assertNull(result.medianGapMinutes)
        assertEquals(0, result.recordedDays)
        assertEquals(0, result.avoided)
        assertEquals(today.minusDays(7), result.days.first().date)
        assertEquals(today.minusDays(1), result.days.last().date)
    }

    @Test fun `snapshots establish zero days but beverage and check-in records do not`() {
        val yesterday = today.minusDays(1)
        val instant = yesterday.atTime(10, 0).atZone(zone).toInstant()
        val drink = BeverageLog("coffee", BeverageType.COFFEE, instant, instant, "TEST", null)
        val unknown = report(drinks = listOf(drink))
        assertEquals(0, unknown.recordedDays)
        assertEquals(1, unknown.days.last().coffee)
        val tracked = report(plans = listOf(plan(yesterday)), drinks = listOf(drink))
        assertEquals(0.0, tracked.average!!, 0.0)
        assertEquals(1, tracked.onPlanDays)
        assertEquals(12, tracked.avoided)
    }

    @Test fun `today future logs and reversed entries cannot improve a completed day comparison`() {
        val yesterday = today.minusDays(1)
        val result = report(listOf(log(today, 8), log(today.plusDays(1), 8), log(yesterday, 9, reversed = true), log(yesterday, 10)))
        assertEquals(1, result.total)
        assertEquals(1, result.recordedDays)
        assertEquals(1.0, result.average!!, 0.0)
    }

    @Test fun `uses historical baseline and ceiling not the current plan`() {
        val yesterday = today.minusDays(1)
        val result = report(listOf(log(yesterday, 8), log(yesterday, 12)), listOf(plan(yesterday, baseline = 6, ceiling = 1)))
        assertEquals(4, result.avoided)
        assertEquals(0, result.onPlanDays)
        assertEquals(1, result.plannedDays)
    }

    @Test fun `compares equally long windows with at least three recorded days each`() {
        val plans = (1..14).map { plan(today.minusDays(it.toLong())) }
        val logs = (1..14).flatMap { offset ->
            (0 until if (offset <= 7) 2 else 4).map { hour -> log(today.minusDays(offset.toLong()), 8 + hour) }
        }
        val result = report(logs, plans)
        assertEquals(2.0, result.average!!, 0.0)
        assertEquals(4.0, result.previousAverage!!, 0.0)
        assertEquals(-50.0, result.changePercent!!, 0.0)
        assertNull(report(logs.take(2)).changePercent)
        assertNull(report(logs.filter { it.occurredAt >= today.minusDays(7).atStartOfDay(zone).toInstant() }, plans).changePercent)
    }

    @Test fun `median handles unsorted logs and ignores overnight intervals`() {
        val yesterday = today.minusDays(1)
        val logs = listOf(log(yesterday, 14), log(yesterday, 9), log(yesterday, 10), log(today.minusDays(2), 8))
        val result = report(logs)
        assertEquals(150L, result.medianGapMinutes)
        assertEquals(240L, result.longestDaytimeGapMinutes)
        assertEquals(2, result.gapsMeasured)
    }

    @Test fun `heatmap buckets use local weekday and local clock`() {
        val result = report(listOf(log(today.minusDays(1), 1), log(today.minusDays(1), 17)))
        assertEquals(1, result.heatmap[0][0]) // Monday in Copenhagen, Sunday in UTC for the first entry.
        assertEquals(1, result.heatmap[0][4])
        assertEquals(2, result.heatmap.flatten().sum())
    }

    @Test fun `only completed paired ratings contribute to tool effects`() {
        val instant = today.minusDays(1).atTime(10, 0).atZone(zone).toInstant()
        fun session(id: String, before: Int?, after: Int?, completed: Boolean, tool: String = "BREATH") =
            UrgeSession(id, instant, instant.plusSeconds(60), tool, before, after, setOf("stress"), null, completed, null, null)
        val result = report(sessions = listOf(session("a", 5, 2, true), session("b", null, 1, true),
            session("c", 4, 1, false), session("d", 4, 1, true, "CHECK_IN")))
        assertEquals(1, result.tools.size)
        assertEquals(2, result.tools.single().completed)
        assertEquals(1, result.tools.single().rated)
        assertEquals(3.0, result.tools.single().averageDrop!!, 0.0)
        assertEquals(4, result.taggedCheckIns)
        assertEquals(4, result.triggers.single().count)
    }

    @Test fun `drink association counts each cigarette once and excludes after and sixty minute boundary`() {
        val yesterday = today.minusDays(1)
        fun drink(hour: Int, minute: Int, reversed: Boolean = false): BeverageLog {
            val instant = yesterday.atTime(hour, minute).atZone(zone).toInstant()
            return BeverageLog("$hour-$minute", BeverageType.COFFEE, instant, instant, "TEST", if (reversed) now else null)
        }
        val result = report(logs = listOf(log(yesterday, 10), log(yesterday, 11), log(yesterday, 12)),
            drinks = listOf(drink(9, 30), drink(9, 45), drink(10, 1, reversed = true), drink(12, 30)))
        assertEquals(1, result.drinkAssociations.first().followingLogs)
        assertEquals(3, result.drinkAssociations.first().totalLogs)
    }

    @Test fun `csv leaves unknown cigarette days blank and uses invariant dates`() {
        val yesterday = today.minusDays(1)
        val csv = report(plans = listOf(plan(yesterday))).toCsv()
        assertTrue(csv.contains("2026-09-01,,false,,,0,0,0,0,\r\n"))
        assertTrue(csv.contains("2026-09-07,0,true,8,12,0,0,0,0,true\r\n"))
        assertEquals(8, csv.trimEnd().lines().size)
    }

    @Test fun `a drink just before the range can precede the first cigarette inside it`() {
        val start = today.minusDays(7)
        val instant = start.minusDays(1).atTime(23, 45).atZone(zone).toInstant()
        val drink = BeverageLog("boundary", BeverageType.COFFEE, instant, instant, "TEST", null)
        val result = report(listOf(log(start, 0, 15)), drinks = listOf(drink))
        assertEquals(1, result.drinkAssociations.first().followingLogs)
        assertEquals(0, result.days.sumOf { it.coffee })
    }

    @Test fun `dst repeat hour measures elapsed time not wall clock subtraction`() {
        val dstToday = LocalDate.of(2026, 10, 26)
        val first = Instant.parse("2026-10-25T00:30:00Z")
        val second = Instant.parse("2026-10-25T01:30:00Z")
        val logs = listOf(first, second).mapIndexed { index, instant -> CigaretteLog("dst-$index", instant, instant, "TEST", null) }
        val result = PaceInsightsCalculator.calculate(dstToday.atStartOfDay(zone).toInstant(), zone, logs, emptyList(), emptyList(), emptyList())
        assertEquals(60L, result.medianGapMinutes)
        assertEquals(2, result.heatmap[6][0])
    }

    @Test fun `range can expand to a full month without dropping old records`() {
        val result = report(listOf(log(today.minusDays(30), 10)), days = 30)
        assertEquals(30, result.days.size)
        assertEquals(1, result.total)
    }

    @Test fun `savings use decimal arithmetic and reward days round up`() {
        assertEquals(BigDecimal("202.50"), SavingsProjection.estimate(3, 30, 45.0, 20))
        assertEquals(15L, SavingsProjection.daysToReward(100.0, 3, 45.0, 20))
        assertEquals(0L, SavingsProjection.daysToReward(-1.0, 0, 0.0, 0))
        assertNull(SavingsProjection.daysToReward(100.0, 0, 45.0, 20))
        assertEquals(BigDecimal("0.00"), SavingsProjection.estimate(3, 30, Double.NaN, 20))
        assertEquals(BigDecimal("0.00"), SavingsProjection.estimate(3, 30, 45.0, 0))
    }
}
