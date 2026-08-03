package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.DailyPlanSnapshot
import com.pace.reduction.domain.model.UrgeSession
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

data class DailyProgress(
    val date: LocalDate,
    val count: Int,
    val ceiling: Int?,
    val recorded: Boolean,
    val completed: Boolean,
    val steady: Boolean?,
)

data class ProgressMetrics(
    val days: List<DailyProgress>,
    val sevenDayAverage: Double?,
    val longestGapMinutes: Long,
    val bestMorningHoldMinutes: Long,
    val steadyDays7: Int,
    val steadyDays30: Int,
    val pausesCompleted: Int,
    val urgesReduced: Int,
    val avoidedCigarettes: Int,
    val estimatedSavings: BigDecimal,
    val rewardProgress: Double,
)

object ProgressCalculator {
    fun calculate(
        today: LocalDate,
        zoneId: ZoneId,
        logs: List<CigaretteLog>,
        snapshots: List<DailyPlanSnapshot>,
        sessions: List<UrgeSession>,
        pricePerPack: Double,
        cigarettesPerPack: Int,
        rewardTarget: Double,
    ): ProgressMetrics {
        val active = logs.filter { it.reversedAt == null }.sortedBy { it.occurredAt }
        val counts = active.groupingBy { it.occurredAt.atZone(zoneId).toLocalDate() }.eachCount()
        val snapshotsByDate = snapshots.associateBy { it.localDate }
        val days = (29 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val snapshot = snapshotsByDate[date]
            val count = counts[date] ?: 0
            DailyProgress(
                date = date,
                count = count,
                ceiling = snapshot?.ceiling,
                recorded = snapshot != null || counts.containsKey(date),
                completed = date.isBefore(today),
                steady = if (date.isBefore(today) && snapshot != null) count <= snapshot.ceiling else null,
            )
        }
        val sevenRecorded = days.takeLast(7).filter { it.recorded }
        val completedSnapshots = snapshots.filter { it.localDate.isBefore(today) }
        val avoided = completedSnapshots.sumOf { snapshot ->
            (snapshot.baseline - (counts[snapshot.localDate] ?: 0)).coerceAtLeast(0)
        }
        val unitPrice = if (cigarettesPerPack > 0) {
            BigDecimal.valueOf(pricePerPack).divide(BigDecimal(cigarettesPerPack), 8, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        val savings = unitPrice.multiply(BigDecimal(avoided)).setScale(2, RoundingMode.HALF_UP)
        val longestGap = active.zipWithNext().maxOfOrNull { (first, second) ->
            Duration.between(first.occurredAt, second.occurredAt).toMinutes().coerceAtLeast(0)
        } ?: 0L
        val firstLogByDay = active.groupBy { it.occurredAt.atZone(zoneId).toLocalDate() }
            .mapValues { (_, values) -> values.minOf { it.occurredAt } }
        val bestMorning = completedSnapshots.maxOfOrNull { snapshot ->
            val wake = snapshot.localDate.atStartOfDay(zoneId).plusMinutes(snapshot.wakeMinutes.toLong()).toInstant()
            val firstLog = firstLogByDay[snapshot.localDate]
            if (firstLog == null) snapshot.morningHoldMinutes.toLong()
            else Duration.between(wake, firstLog).toMinutes().coerceAtLeast(0)
        } ?: 0L

        return ProgressMetrics(
            days = days,
            sevenDayAverage = sevenRecorded.takeIf { it.isNotEmpty() }?.map { it.count }?.average(),
            longestGapMinutes = longestGap,
            bestMorningHoldMinutes = bestMorning,
            steadyDays7 = days.takeLast(7).count { it.steady == true },
            steadyDays30 = days.count { it.steady == true },
            pausesCompleted = sessions.count { it.completed && it.tool == "PAUSE" },
            urgesReduced = sessions.count {
                it.completed && it.urgeBefore != null && it.urgeAfter != null && it.urgeAfter < it.urgeBefore
            },
            avoidedCigarettes = avoided,
            estimatedSavings = savings,
            rewardProgress = if (rewardTarget > 0) (savings.toDouble() / rewardTarget).coerceIn(0.0, 1.0) else 0.0,
        )
    }
}

data class BadgeCandidate(val id: String, val evidence: String)

object BadgeEngine {
    /**
     * Rolls the user's totals up into the tiered catalogue. Awarding is idempotent: the DAO ignores
     * duplicate badge ids, so re-evaluating simply re-confirms what is already earned.
     */
    fun eligible(
        metrics: ProgressMetrics,
        logs: List<CigaretteLog>,
        snapshots: List<DailyPlanSnapshot>,
        sessions: List<UrgeSession>,
        zoneId: ZoneId,
        quit: QuitMetrics,
        conversationCount: Int = 0,
    ): List<BadgeCandidate> {
        val cleanMinutes = quit.smokeFreeDuration.toMinutes()
        val totals = mapOf(
            BadgeFamily.CLEAN_HOURS to cleanMinutes / 60,
            BadgeFamily.CLEAN_DAYS to cleanMinutes / (24 * 60),
            BadgeFamily.CLEAR_STREAK to quit.zeroDayStreak.toLong(),
            BadgeFamily.RESISTED to quit.cigarettesAvoided.toLong(),
            BadgeFamily.SAVED to quit.moneySaved.toLong(),
            BadgeFamily.STEADY_DAYS to metrics.days.count { it.steady == true }.toLong(),
            BadgeFamily.TOOLS to sessions.count { it.completed }.toLong(),
            BadgeFamily.CONVERSATIONS to conversationCount.toLong(),
            BadgeFamily.LONGEST_WAIT to metrics.longestGapMinutes,
            BadgeFamily.TIME_BACK to quit.minutesOfLifeRegained,
        )
        return BadgeCatalogue.earned(totals).map { definition ->
            BadgeCandidate(definition.id, definition.threshold.toString())
        }
    }
}
