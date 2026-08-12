package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.DailyPlanSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Where a day sits against the allowance it was given, which is what the grid colours by. */
enum class DayStanding {
    /** No plan and nothing logged: before the app knew about this day. */
    UNKNOWN,

    /** Nothing logged on a day the app was watching. */
    CLEAR,
    UNDER,
    AT,
    OVER,
}

data class CalendarDay(
    val date: LocalDate,
    val count: Int,
    val ceiling: Int?,
    val standing: DayStanding,
    val future: Boolean,
)

/** Seven slots, Monday-first or Sunday-first per the locale; null where the week runs past the range. */
data class CalendarWeek(val start: LocalDate, val days: List<CalendarDay?>)

data class CalendarHistory(
    val weeks: List<CalendarWeek>,
    val firstDayOfWeek: DayOfWeek,
    /** Days that were watched and stayed at or under their ceiling, across the whole grid. */
    val steadyDays: Int,
    val recordedDays: Int,
    val clearDays: Int,
) {
    val isEmpty: Boolean get() = recordedDays == 0
}

/**
 * A calendar of every day the app has watched, for reading the shape of a month at a glance.
 *
 * The thirty-day bar chart it replaces could only answer "how was yesterday"; the question people
 * actually bring to a history screen is which stretches went well and which fell apart, and that is
 * a question about weeks sitting under each other rather than days in a row.
 *
 * Days before the first plan are [DayStanding.UNKNOWN] rather than zero. A grid that draws the
 * month before you started as a wall of perfect days is a grid that lies.
 */
object CalendarHistoryBuilder {
    /** Thirteen weeks: a quarter is long enough for a pattern to show and short enough to scan. */
    const val DEFAULT_WEEKS = 13

    fun build(
        today: LocalDate,
        zoneId: ZoneId,
        logs: List<CigaretteLog>,
        snapshots: List<DailyPlanSnapshot>,
        weeks: Int = DEFAULT_WEEKS,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): CalendarHistory {
        require(weeks > 0) { "weeks must be positive" }
        val counts = logs.asSequence()
            .filter { it.reversedAt == null }
            .groupingBy { it.occurredAt.atZone(zoneId).toLocalDate() }
            .eachCount()
        val snapshotsByDate = snapshots.associateBy { it.localDate }

        // Start on the week boundary so every row is a full week and the columns line up under
        // their weekday headings.
        val lastWeekStart = today.previousOrSame(firstDayOfWeek)
        val firstWeekStart = lastWeekStart.minusWeeks((weeks - 1).toLong())

        val rows = (0 until weeks).map { index ->
            val start = firstWeekStart.plusWeeks(index.toLong())
            CalendarWeek(
                start = start,
                days = (0 until 7).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    if (date.isAfter(today)) {
                        // Kept rather than dropped: the current week should hold its shape instead
                        // of the row shrinking as the week fills in.
                        CalendarDay(date, 0, null, DayStanding.UNKNOWN, future = true)
                    } else {
                        val ceiling = snapshotsByDate[date]?.ceiling
                        val count = counts[date] ?: 0
                        CalendarDay(
                            date = date,
                            count = count,
                            ceiling = ceiling,
                            standing = standingFor(count, ceiling, counts.containsKey(date)),
                            future = false,
                        )
                    }
                },
            )
        }

        val known = rows.flatMap { it.days }.filterNotNull()
            .filter { !it.future && it.standing != DayStanding.UNKNOWN }
        return CalendarHistory(
            weeks = rows,
            firstDayOfWeek = firstDayOfWeek,
            steadyDays = known.count { it.standing != DayStanding.OVER },
            recordedDays = known.size,
            clearDays = known.count { it.standing == DayStanding.CLEAR },
        )
    }

    private fun standingFor(count: Int, ceiling: Int?, logged: Boolean): DayStanding = when {
        ceiling == null && !logged -> DayStanding.UNKNOWN
        count == 0 -> DayStanding.CLEAR
        // Logged on a day with no plan behind it — an import, or a day edited into history. It
        // happened, so it is drawn, but there is no allowance to judge it against.
        ceiling == null -> DayStanding.AT
        count < ceiling -> DayStanding.UNDER
        count == ceiling -> DayStanding.AT
        else -> DayStanding.OVER
    }
}

/** The most recent [DayOfWeek] on or before this date. */
private fun LocalDate.previousOrSame(day: DayOfWeek): LocalDate {
    val back = ((dayOfWeek.value - day.value) + 7) % 7
    return minus(back.toLong(), ChronoUnit.DAYS)
}
