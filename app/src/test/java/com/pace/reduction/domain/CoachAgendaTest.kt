package com.pace.reduction.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachAgendaTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val wake = 7 * 60
    private val sleep = 22 * 60 + 30
    private val workStart = 9 * 60
    private val workEnd = 17 * 60

    /** A Tuesday, so the weekday branch is the one under test unless a case says otherwise. */
    private val weekday: LocalDate = LocalDate.of(2026, 8, 11)
    private val saturday: LocalDate = LocalDate.of(2026, 8, 15)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = weekday): ZonedDateTime =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone)

    private fun due(
        now: ZonedDateTime,
        state: AgendaState = AgendaState(),
        maxMoveInvites: Int = 2,
    ) = CoachAgenda.due(now, wake, sleep, workStart, workEnd, state, maxMoveInvites)

    @Test
    fun theMorningPlanLandsShortlyAfterWaking() {
        assertEquals(CoachBeat.MORNING_PLAN, due(at(7, 20)))
        assertEquals(CoachBeat.MORNING_PLAN, due(at(8, 30)))
    }

    @Test
    fun theMorningPlanGoesOutOnceADay() {
        val state = AgendaState(morningSentOn = weekday)

        assertNull(due(at(7, 20), state))
        // …and is due again the next morning.
        assertEquals(CoachBeat.MORNING_PLAN, due(at(7, 20, weekday.plusDays(1)), state))
    }

    @Test
    fun theEveningLineArrivesBeforeBedRatherThanAtIt() {
        assertEquals(CoachBeat.EVENING_REFLECT, due(at(21, 0)))
        // Too close to the stated bedtime to be worth waking a phone for.
        assertNull(due(at(22, 20)))
    }

    @Test
    fun invitationsToMoveOnlyFallInsideTheStatedDay() {
        assertEquals(CoachBeat.MOVE_INVITE, due(at(11, 0)))
        // Before the day starts the morning window owns the hour; after it ends, nothing does.
        assertNull(due(at(18, 30)))
    }

    @Test
    fun invitationsToMoveRespectTheirCapAndTheirGap() {
        val justSent = AgendaState(
            moveInvitesOn = weekday,
            moveInvitesToday = 1,
            lastMoveInviteAt = at(10, 30).toInstant(),
        )
        assertNull("two invitations inside the gap", due(at(11, 0), justSent))

        val longEnoughAgo = justSent.copy(lastMoveInviteAt = at(9, 0).toInstant())
        assertEquals(CoachBeat.MOVE_INVITE, due(at(11, 45), longEnoughAgo))

        val capped = longEnoughAgo.copy(moveInvitesToday = 2)
        assertNull("past the daily cap", due(at(11, 45), capped))
    }

    @Test
    fun yesterdaysInvitationCountDoesNotCarryOver() {
        val yesterday = AgendaState(
            moveInvitesOn = weekday.minusDays(1),
            moveInvitesToday = 2,
            lastMoveInviteAt = at(16, 0, weekday.minusDays(1)).toInstant(),
        )

        assertEquals(CoachBeat.MOVE_INVITE, due(at(11, 0), yesterday))
    }

    @Test
    fun nothingIsDueOvernight() {
        assertNull(due(at(23, 30)))
        assertNull(due(at(3, 0)))
        assertNull(due(at(6, 30)))
    }

    @Test
    fun morningOutranksTheOthersWhenTheyOverlap() {
        // A late riser whose working day has already begun still gets the plan first.
        val lateStart = CoachAgenda.due(
            now = at(9, 30),
            wakeMinutes = 9 * 60,
            sleepMinutes = sleep,
            workStartMinutes = workStart,
            workEndMinutes = workEnd,
            state = AgendaState(),
            maxMoveInvites = 2,
        )

        assertEquals(CoachBeat.MORNING_PLAN, lateStart)
    }

    @Test
    fun theWeekendUsesTheWakingDayRatherThanOfficeHours() {
        // 18:30 is outside working hours but squarely inside a Saturday afternoon.
        assertEquals(CoachBeat.MOVE_INVITE, due(at(18, 30, saturday)))
    }

    @Test
    fun aBedtimePastMidnightStillGetsItsEveningLine() {
        val nightOwl = { hour: Int, minute: Int ->
            CoachAgenda.due(
                now = at(hour, minute),
                wakeMinutes = 9 * 60,
                sleepMinutes = 30, // 00:30
                workStartMinutes = workStart,
                workEndMinutes = workEnd,
                state = AgendaState(),
                maxMoveInvites = 2,
            )
        }

        assertEquals(CoachBeat.EVENING_REFLECT, nightOwl(23, 0))
        assertNull(nightOwl(2, 0))
    }

    @Test
    fun theGapIsMeasuredInRealTimeNotInWakeUps() {
        val state = AgendaState(
            moveInvitesOn = weekday,
            moveInvitesToday = 1,
            lastMoveInviteAt = at(11, 0).toInstant(),
        )
        val exactlyOnTheGap = at(11, 0).plus(Duration.ofMinutes(CoachAgenda.MOVE_GAP_MINUTES))

        assertEquals(CoachBeat.MOVE_INVITE, due(exactlyOnTheGap, state))
    }
}
