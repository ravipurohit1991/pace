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
    fun eligible(
        metrics: ProgressMetrics,
        logs: List<CigaretteLog>,
        snapshots: List<DailyPlanSnapshot>,
        sessions: List<UrgeSession>,
        zoneId: ZoneId,
    ): List<BadgeCandidate> = buildList {
        val active = logs.filter { it.reversedAt == null }.sortedBy { it.occurredAt }
        if (sessions.any { it.completed && it.tool == "PAUSE" }) {
            add(BadgeCandidate("first_pause", "Completed a five-minute pause"))
        }
        val loggedDays = active.map { it.occurredAt.atZone(zoneId).toLocalDate() }.distinct().size
        if (loggedDays >= 7) add(BadgeCandidate("honest_week", "Logged honestly on $loggedDays distinct days"))
        val startingGap = snapshots.minByOrNull { it.localDate }?.minimumGapMinutes
        if (startingGap != null && metrics.longestGapMinutes >= startingGap + 30L) {
            add(BadgeCandidate("space_maker", "Created a ${metrics.longestGapMinutes}-minute gap"))
        }
        if (metrics.bestMorningHoldMinutes > 0) {
            val completedHolds = snapshots.count { snapshot ->
                val first = active.firstOrNull { it.occurredAt.atZone(zoneId).toLocalDate() == snapshot.localDate }
                first == null || first.occurredAt.atZone(zoneId).toLocalTime().toSecondOfDay() / 60 >=
                    snapshot.wakeMinutes + snapshot.morningHoldMinutes
            }
            if (completedHolds >= 5) add(BadgeCandidate("morning_reclaimed", "Completed the morning hold on $completedHolds days"))
        }
        if (metrics.days.takeLast(4).count { it.steady == true } >= 3) {
            add(BadgeCandidate("steady_three", "Finished three of the last four days at or below plan"))
        }
        val completedTools = sessions.count { it.completed }
        if (completedTools >= 10) add(BadgeCandidate("tool_builder", "Completed $completedTools toolkit sessions"))
        if (metrics.avoidedCigarettes >= 10) {
            add(BadgeCandidate("ten_avoided", "Estimated ${metrics.avoidedCigarettes} cigarettes avoided on completed days"))
        }
        if (metrics.rewardProgress >= 0.25) add(BadgeCandidate("reward_step", "Reached 25% of the chosen reward target"))
    }
}
