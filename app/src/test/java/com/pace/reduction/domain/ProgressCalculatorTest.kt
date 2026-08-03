package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.DailyPlanSnapshot
import com.pace.reduction.domain.model.UrgeSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCalculatorTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private val today = LocalDate.of(2026, 8, 10)

    @Test
    fun completedSnapshotsDriveAvoidedSavingsAndUnknownDaysStayUnknown() {
        val snapshots = listOf(
            snapshot(today.minusDays(2), baseline = 10, ceiling = 8),
            snapshot(today.minusDays(1), baseline = 10, ceiling = 8),
        )
        val logs = (0 until 7).map { index -> log("a$index", "2026-08-08T${10 + index}:00:00Z") } +
            (0 until 9).map { index -> log("b$index", "2026-08-09T${10 + index}:00:00Z") }

        val result = ProgressCalculator.calculate(today, zone, logs, snapshots, emptyList(), 60.0, 20, 100.0)

        assertEquals(4, result.avoidedCigarettes)
        assertEquals("12.00", result.estimatedSavings.toPlainString())
        assertEquals(1, result.steadyDays7)
        assertFalse(result.days.first().recorded)
        assertNull(result.days.first().steady)
    }

    @Test
    fun urgeReductionAndCompletedPauseAreCounted() {
        val sessions = listOf(
            session("one", "PAUSE", 5, 2, true),
            session("two", "MEMORY", 3, 4, true),
            session("three", "PAUSE", 4, null, false),
        )
        val result = ProgressCalculator.calculate(today, zone, emptyList(), emptyList(), sessions, 0.0, 20, 0.0)

        assertEquals(1, result.pausesCompleted)
        assertEquals(1, result.urgesReduced)
    }

    @Test
    fun reductionSuggestionRequiresFullSteadyEvidence() {
        val days = (1..7).map { index ->
            DailyProgress(today.minusDays(index.toLong()), 6, 8, recorded = true, completed = true, steady = index != 7)
        }
        assertEquals(7, ReductionPlanner.suggestedCeiling(8, 1, 7, days))
        assertNull(ReductionPlanner.suggestedCeiling(8, 1, 8, days))
    }

    @Test
    fun badgeCandidatesAreUniqueAndTieredFromTotals() {
        val sessions = (1..10).map { session("session-$it", if (it == 1) "PAUSE" else "MEMORY", 4, 2, true) }
        val metrics = ProgressCalculator.calculate(today, zone, emptyList(), emptyList(), sessions, 0.0, 20, 0.0)
        val quit = QuitProgress.calculate(
            now = today.atStartOfDay(zone).toInstant(),
            zoneId = zone,
            logs = emptyList(),
            baselinePerDay = 10,
            pricePerPack = 0.0,
            cigarettesPerPack = 20,
            quitDate = today.minusDays(3),
        )
        val badges = BadgeEngine.eligible(metrics, emptyList(), emptyList(), sessions, zone, quit, conversationCount = 3)

        assertEquals(badges.map { it.id }.distinct(), badges.map { it.id })
        // Ten completed sessions clears every toolkit tier up to ten.
        assertTrue(badges.any { it.id == "tools_1" })
        assertTrue(badges.any { it.id == "tools_10" })
        assertFalse(badges.any { it.id == "tools_15" })
        // Three chats clears the first three conversation tiers only.
        assertTrue(badges.any { it.id == "conversations_3" })
        assertFalse(badges.any { it.id == "conversations_5" })
    }

    private fun snapshot(date: LocalDate, baseline: Int, ceiling: Int) =
        DailyPlanSnapshot(date, baseline, ceiling, 60, 420, 30)

    private fun log(id: String, timestamp: String): CigaretteLog {
        val instant = Instant.parse(timestamp)
        return CigaretteLog(id, instant, instant, "APP", null)
    }

    private fun session(id: String, tool: String, before: Int?, after: Int?, completed: Boolean) = UrgeSession(
        id = id,
        startedAt = Instant.EPOCH,
        endedAt = if (completed) Instant.EPOCH else null,
        tool = tool,
        urgeBefore = before,
        urgeAfter = after,
        triggerTags = emptySet(),
        note = null,
        completed = completed,
        smokedAfter = null,
        externalRef = null,
    )
}
