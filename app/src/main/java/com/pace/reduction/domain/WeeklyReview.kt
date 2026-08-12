package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.DailyPlanSnapshot
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class WeekSummary(
    val start: LocalDate,
    /** How many days of this week the figures cover — never more than have actually happened. */
    val daysCounted: Int,
    val logged: Int,
    val clearDays: Int,
    val steadyDays: Int,
    val recordedDays: Int,
    val longestGapMinutes: Long,
    val moneySaved: BigDecimal,
)

/**
 * This week against last, compared over the same number of days.
 *
 * Everything here is already computed somewhere — the coach is handed a week-over-week line before
 * every reply — but the person it is about has never been shown it. A trend is the one thing a
 * single day cannot tell you, and it is the thing that decides whether the plan is working.
 *
 * The comparison is deliberately like-for-like: on a Tuesday it weighs two days against the first
 * two days of last week, not against last week's full seven. Comparing a part to a whole always
 * flatters the part, and a progress screen that congratulates you every Monday morning is worth
 * nothing by Friday.
 */
data class WeeklyReview(
    val thisWeek: WeekSummary,
    val lastWeek: WeekSummary?,
    /** True until this week is over, which is what makes the trimmed comparison worth explaining. */
    val partial: Boolean,
) {
    /** Negative is progress: fewer logged than the same stretch last week. */
    val loggedDelta: Int? get() = lastWeek?.let { thisWeek.logged - it.logged }

    val clearDaysDelta: Int? get() = lastWeek?.let { thisWeek.clearDays - it.clearDays }

    val longestGapDelta: Long? get() = lastWeek?.let { thisWeek.longestGapMinutes - it.longestGapMinutes }

    /** Only worth drawing once there is a week behind it with something in it. */
    val comparable: Boolean get() = lastWeek != null && lastWeek.recordedDays > 0
}

object WeeklyReviewCalculator {
    fun calculate(
        today: LocalDate,
        zoneId: ZoneId,
        logs: List<CigaretteLog>,
        snapshots: List<DailyPlanSnapshot>,
        pricePerPack: Double,
        cigarettesPerPack: Int,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): WeeklyReview {
        val active = logs.filter { it.reversedAt == null }.sortedBy { it.occurredAt }
        val counts = active.groupingBy { it.occurredAt.atZone(zoneId).toLocalDate() }.eachCount()
        val snapshotsByDate = snapshots.associateBy { it.localDate }
        val unitPrice = if (cigarettesPerPack > 0) {
            BigDecimal.valueOf(pricePerPack).divide(BigDecimal(cigarettesPerPack), 8, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val thisStart = today.previousOrSame(firstDayOfWeek)
        val elapsed = (ChronoUnit.DAYS.between(thisStart, today) + 1).toInt()
        val lastStart = thisStart.minusWeeks(1)

        fun summarise(start: LocalDate, days: Int): WeekSummary {
            val dates = (0 until days).map { start.plusDays(it.toLong()) }
            val watched = dates.filter { snapshotsByDate.containsKey(it) || counts.containsKey(it) }
            val windowStart = start.atStartOfDay(zoneId).toInstant()
            val windowEnd = start.plusDays(days.toLong()).atStartOfDay(zoneId).toInstant()
            // A gap belongs to the week the wait ended in, which is the week the person served it.
            val longestGap = active.asSequence()
                .zipWithNext()
                .filter { (_, second) -> !second.occurredAt.isBefore(windowStart) && second.occurredAt.isBefore(windowEnd) }
                .maxOfOrNull { (first, second) ->
                    Duration.between(first.occurredAt, second.occurredAt).toMinutes().coerceAtLeast(0)
                } ?: 0L
            val avoided = dates.sumOf { date ->
                val snapshot = snapshotsByDate[date] ?: return@sumOf 0
                (snapshot.baseline - (counts[date] ?: 0)).coerceAtLeast(0)
            }
            return WeekSummary(
                start = start,
                daysCounted = days,
                logged = dates.sumOf { counts[it] ?: 0 },
                clearDays = watched.count { (counts[it] ?: 0) == 0 },
                steadyDays = watched.count { date ->
                    val ceiling = snapshotsByDate[date]?.ceiling ?: return@count false
                    (counts[date] ?: 0) <= ceiling
                },
                recordedDays = watched.size,
                longestGapMinutes = longestGap,
                moneySaved = unitPrice.multiply(BigDecimal(avoided)).setScale(2, RoundingMode.HALF_UP),
            )
        }

        return WeeklyReview(
            thisWeek = summarise(thisStart, elapsed),
            lastWeek = summarise(lastStart, elapsed),
            partial = elapsed < 7,
        )
    }
}

/** The most recent [DayOfWeek] on or before this date. */
private fun LocalDate.previousOrSame(day: DayOfWeek): LocalDate {
    val back = ((dayOfWeek.value - day.value) + 7) % 7
    return minus(back.toLong(), ChronoUnit.DAYS)
}
