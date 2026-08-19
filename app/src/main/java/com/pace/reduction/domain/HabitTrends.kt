package com.pace.reduction.domain

import com.pace.reduction.domain.model.BeverageLog
import com.pace.reduction.domain.model.BeverageType
import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.UrgeSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class HabitMetric { CIGARETTES, COFFEE, ALCOHOL, OTHER, CHECK_INS, STEPS }

data class HabitWeek(
    val start: LocalDate,
    val cigarettes: Int,
    val coffee: Int,
    val alcohol: Int,
    val other: Int,
    val checkIns: Int,
    val steps: Int,
) {
    operator fun get(metric: HabitMetric): Int = when (metric) {
        HabitMetric.CIGARETTES -> cigarettes
        HabitMetric.COFFEE -> coffee
        HabitMetric.ALCOHOL -> alcohol
        HabitMetric.OTHER -> other
        HabitMetric.CHECK_INS -> checkIns
        HabitMetric.STEPS -> steps
    }
}

data class HabitTrend(val weeks: List<HabitWeek>) {
    fun values(metric: HabitMetric): List<Int> = weeks.map { it[metric] }
    fun total(metric: HabitMetric): Int = values(metric).sum()
    fun average(metric: HabitMetric): Double = values(metric).average().takeUnless(Double::isNaN) ?: 0.0
    fun latestChange(metric: HabitMetric): Int? = weeks.takeLast(2)
        .takeIf { it.size == 2 }
        ?.let { it[1][metric] - it[0][metric] }
}

object HabitTrendBuilder {
    const val DEFAULT_WEEKS = 8

    fun build(
        today: LocalDate,
        zoneId: ZoneId,
        cigarettes: List<CigaretteLog>,
        beverages: List<BeverageLog>,
        sessions: List<UrgeSession> = emptyList(),
        stepDays: List<StepDay> = emptyList(),
        weeks: Int = DEFAULT_WEEKS,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): HabitTrend {
        require(weeks > 0) { "weeks must be positive" }
        val currentWeek = today.previousOrSame(firstDayOfWeek)
        val firstWeek = currentWeek.minusWeeks((weeks - 1).toLong())
        val cigaretteDates = cigarettes.asSequence()
            .filter { it.reversedAt == null }
            .groupingBy { it.occurredAt.atZone(zoneId).toLocalDate() }
            .eachCount()
        val beverageDates = beverages.asSequence()
            .filter { it.reversedAt == null }
            .groupBy { it.occurredAt.atZone(zoneId).toLocalDate() }
        val sessionDates = sessions.groupingBy { it.startedAt.atZone(zoneId).toLocalDate() }.eachCount()
        val stepsByDate = stepDays.associateBy(StepDay::date)

        return HabitTrend(
            weeks = (0 until weeks).map { index ->
                val start = firstWeek.plusWeeks(index.toLong())
                val dates = (0 until 7).map { start.plusDays(it.toLong()) }
                    .filterNot { it.isAfter(today) }
                val drinks = dates.flatMap { beverageDates[it].orEmpty() }
                HabitWeek(
                    start = start,
                    cigarettes = dates.sumOf { cigaretteDates[it] ?: 0 },
                    coffee = drinks.count { it.type == BeverageType.COFFEE },
                    alcohol = drinks.count { it.type == BeverageType.ALCOHOL },
                    other = drinks.count { it.type == BeverageType.OTHER },
                    checkIns = dates.sumOf { sessionDates[it] ?: 0 },
                    steps = dates.sumOf {
                        (stepsByDate[it]?.steps ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    },
                )
            },
        )
    }
}

private fun LocalDate.previousOrSame(day: DayOfWeek): LocalDate {
    val daysBack = ((dayOfWeek.value - day.value) + 7) % 7
    return minusDays(daysBack.toLong())
}
