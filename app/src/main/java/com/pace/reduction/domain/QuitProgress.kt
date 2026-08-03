package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A body-recovery milestone reached after a given smoke-free duration.
 * Timings follow the widely published CDC / NHS cessation timeline.
 */
data class RecoveryMilestone(
    val id: String,
    val afterMinutes: Long,
    val reached: Boolean,
    val progress: Float,
)

data class QuitMetrics(
    /** Time since the most recent cigarette, or since the quit date when nothing is logged. */
    val smokeFreeDuration: Duration,
    val smokeFreeDays: Int,
    /** Consecutive whole days with zero logged cigarettes, ending yesterday or today. */
    val zeroDayStreak: Int,
    val bestZeroDayStreak: Int,
    val cigarettesAvoided: Int,
    val minutesOfLifeRegained: Long,
    val moneySaved: Double,
    val milestones: List<RecoveryMilestone>,
    val nextMilestone: RecoveryMilestone?,
)

object QuitProgress {
    /** Minutes of life expectancy commonly attributed to a single cigarette (BMJ 2000). */
    const val MINUTES_PER_CIGARETTE = 11L

    private val MILESTONE_MINUTES = listOf(
        "heart_rate" to 20L,
        "carbon_monoxide" to 12 * 60L,
        "heart_attack_risk" to 24 * 60L,
        "smell_taste" to 48 * 60L,
        "nicotine_clear" to 72 * 60L,
        "circulation" to 14 * 24 * 60L,
        "lung_function" to 30 * 24 * 60L,
        "breathing" to 90 * 24 * 60L,
        "cilia" to 270 * 24 * 60L,
        "heart_disease" to 365 * 24 * 60L,
        "stroke_risk" to 5 * 365 * 24 * 60L,
        "lung_cancer" to 10 * 365 * 24 * 60L,
    )

    fun calculate(
        now: Instant,
        zoneId: ZoneId,
        logs: List<CigaretteLog>,
        baselinePerDay: Int,
        pricePerPack: Double,
        cigarettesPerPack: Int,
        quitDate: LocalDate?,
    ): QuitMetrics {
        val active = logs.filter { it.reversedAt == null }.sortedBy { it.occurredAt }
        val today = now.atZone(zoneId).toLocalDate()
        val anchor = active.lastOrNull()?.occurredAt
            ?: quitDate?.atStartOfDay(zoneId)?.toInstant()
            ?: now
        val smokeFree = Duration.between(anchor, now).coerceAtLeast(Duration.ZERO)

        val perDay = active.groupingBy { it.occurredAt.atZone(zoneId).toLocalDate() }.eachCount()
        val firstTrackedDay = active.firstOrNull()?.occurredAt?.atZone(zoneId)?.toLocalDate()
            ?: quitDate
            ?: today

        val zeroStreak = generateSequence(today) { it.minusDays(1) }
            .takeWhile { !it.isBefore(firstTrackedDay) }
            .takeWhile { (perDay[it] ?: 0) == 0 }
            .count()

        val bestStreak = bestZeroRun(firstTrackedDay, today, perDay)

        val trackedDays = (Duration.between(
            firstTrackedDay.atStartOfDay(zoneId).toInstant(),
            now,
        ).toMinutes() / (24 * 60.0)).coerceAtLeast(0.0)
        val expected = baselinePerDay * trackedDays
        val avoided = (expected - active.size).toInt().coerceAtLeast(0)

        val perCigarettePrice = if (cigarettesPerPack > 0) pricePerPack / cigarettesPerPack else 0.0
        val milestones = MILESTONE_MINUTES.map { (id, minutes) ->
            val elapsed = smokeFree.toMinutes()
            RecoveryMilestone(
                id = id,
                afterMinutes = minutes,
                reached = elapsed >= minutes,
                progress = (elapsed.toFloat() / minutes.toFloat()).coerceIn(0f, 1f),
            )
        }

        return QuitMetrics(
            smokeFreeDuration = smokeFree,
            smokeFreeDays = (smokeFree.toMinutes() / (24 * 60)).toInt(),
            zeroDayStreak = zeroStreak,
            bestZeroDayStreak = maxOf(bestStreak, zeroStreak),
            cigarettesAvoided = avoided,
            minutesOfLifeRegained = avoided * MINUTES_PER_CIGARETTE,
            moneySaved = avoided * perCigarettePrice,
            milestones = milestones,
            nextMilestone = milestones.firstOrNull { !it.reached },
        )
    }

    private fun bestZeroRun(from: LocalDate, to: LocalDate, perDay: Map<LocalDate, Int>): Int {
        var best = 0
        var run = 0
        var cursor = from
        while (!cursor.isAfter(to)) {
            if ((perDay[cursor] ?: 0) == 0) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            cursor = cursor.plusDays(1)
        }
        return best
    }
}
