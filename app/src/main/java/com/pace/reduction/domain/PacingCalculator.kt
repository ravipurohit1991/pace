package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.PacingStatus
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.QuietSchedule
import com.pace.reduction.domain.model.TodaySummary
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object PacingCalculator {
    fun calculate(
        now: Instant,
        zoneId: ZoneId,
        settings: PlanSettings,
        logs: List<CigaretteLog>,
    ): TodaySummary {
        val nowZoned = now.atZone(zoneId)
        val today = nowZoned.toLocalDate()
        val activeToday = logs
            .asSequence()
            .filter { it.reversedAt == null }
            .filter { it.occurredAt.atZone(zoneId).toLocalDate() == today }
            .sortedBy { it.occurredAt }
            .toList()
        val lastLog = activeToday.lastOrNull()
        val count = activeToday.size
        val schedule = scheduleFor(today, settings)

        val status = when {
            isQuiet(nowZoned.toLocalTime(), schedule) ->
                PacingStatus.Rest(nextWakeAfter(nowZoned, settings))

            count > settings.dailyCeiling -> PacingStatus.Recovery
            count == settings.dailyCeiling -> PacingStatus.CeilingReached
            else -> {
                val wakeHoldEnd = ZonedDateTime.of(today, schedule.wake, zoneId)
                    .plusMinutes(settings.morningHoldMinutes.toLong())
                val gapEnd = lastLog?.occurredAt
                    ?.atZone(zoneId)
                    ?.plusMinutes(settings.minimumGapMinutes.toLong())
                var earliest = listOfNotNull(wakeHoldEnd, gapEnd).maxOrNull() ?: wakeHoldEnd

                if (isQuiet(earliest.toLocalTime(), scheduleFor(earliest.toLocalDate(), settings))) {
                    earliest = nextWakeAfter(earliest, settings)
                        .plusMinutes(settings.morningHoldMinutes.toLong())
                }

                when {
                    nowZoned.isBefore(wakeHoldEnd) -> PacingStatus.MorningHold(wakeHoldEnd)
                    nowZoned.isBefore(earliest) -> PacingStatus.Spacing(earliest)
                    else -> PacingStatus.WindowMet(earliest)
                }
            }
        }

        return TodaySummary(
            localDate = today,
            count = count,
            ceiling = settings.dailyCeiling,
            lastLogAt = lastLog?.occurredAt,
            status = status,
        )
    }

    fun scheduleFor(date: LocalDate, settings: PlanSettings): QuietSchedule {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val wakeMinutes = if (weekend && settings.weekendWakeEnabled) {
            settings.weekendWakeMinutes
        } else {
            settings.wakeMinutes
        }
        return QuietSchedule(
            wake = LocalTime.of(wakeMinutes / 60, wakeMinutes % 60),
            sleep = LocalTime.of(settings.sleepMinutes / 60, settings.sleepMinutes % 60),
        )
    }

    fun isQuiet(time: LocalTime, schedule: QuietSchedule): Boolean {
        require(schedule.wake != schedule.sleep) { "Wake and sleep times must differ" }
        return if (schedule.sleep > schedule.wake) {
            time >= schedule.sleep || time < schedule.wake
        } else {
            time >= schedule.sleep && time < schedule.wake
        }
    }

    fun remaining(now: Instant, target: ZonedDateTime): Duration =
        Duration.between(now, target.toInstant()).coerceAtLeast(Duration.ZERO)

    private fun nextWakeAfter(from: ZonedDateTime, settings: PlanSettings): ZonedDateTime {
        val todaySchedule = scheduleFor(from.toLocalDate(), settings)
        val todayWake = ZonedDateTime.of(from.toLocalDate(), todaySchedule.wake, from.zone)
        if (from.isBefore(todayWake)) return todayWake

        val nextDate = from.toLocalDate().plusDays(1)
        return ZonedDateTime.of(nextDate, scheduleFor(nextDate, settings).wake, from.zone)
    }
}

