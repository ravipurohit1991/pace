package com.pace.reduction.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The unprompted moments the coach owns, beyond reacting to a craving.
 *
 * A nudge fires because a window is close and a check-in fires because time has passed. These fire
 * because of where the user is in their own day, which is what turns the coach from something you
 * open into something that is already there.
 */
enum class CoachBeat {
    /** Shortly after waking: the shape of the day, and something to move with. */
    MORNING_PLAN,

    /** Inside working hours: get up, go outside, come back different. */
    MOVE_INVITE,

    /** An hour or two before bed: what today was, and nothing to do about it now. */
    EVENING_REFLECT,
}

/** What has already been sent, so nothing repeats itself. */
data class AgendaState(
    val morningSentOn: LocalDate? = null,
    val eveningSentOn: LocalDate? = null,
    val moveInvitesOn: LocalDate? = null,
    val moveInvitesToday: Int = 0,
    val lastMoveInviteAt: Instant? = null,
)

object CoachAgenda {
    /** How long after waking the morning message still makes sense. */
    private const val MORNING_WINDOW_MINUTES = 90

    /** The evening message lands in this band before the stated bedtime. */
    private const val EVENING_EARLIEST_BEFORE_SLEEP = 150
    private const val EVENING_LATEST_BEFORE_SLEEP = 40

    /** Never two invitations to move inside this many minutes, whatever else is true. */
    const val MOVE_GAP_MINUTES = 150L

    /**
     * The beat that is due now, or null.
     *
     * Ordered rather than combined: two proactive notifications inside a minute is one app being
     * needy, not two useful thoughts. Morning wins because it is the one with a plan in it.
     */
    fun due(
        now: ZonedDateTime,
        wakeMinutes: Int,
        sleepMinutes: Int,
        workStartMinutes: Int,
        workEndMinutes: Int,
        state: AgendaState,
        maxMoveInvites: Int,
        weekend: Boolean = now.dayOfWeek.value >= 6,
    ): CoachBeat? {
        val today = now.toLocalDate()
        val minuteOfDay = now.hour * 60 + now.minute
        val sinceWake = wrapped(minuteOfDay - wakeMinutes)
        val untilSleep = wrapped(sleepMinutes - minuteOfDay)

        // Asleep, as far as the plan is concerned. Nothing is due.
        if (untilSleep == 0 || sinceWake > wrapped(sleepMinutes - wakeMinutes)) return null

        if (state.morningSentOn != today && sinceWake in 0..MORNING_WINDOW_MINUTES) {
            return CoachBeat.MORNING_PLAN
        }
        if (state.eveningSentOn != today &&
            untilSleep in EVENING_LATEST_BEFORE_SLEEP..EVENING_EARLIEST_BEFORE_SLEEP
        ) {
            return CoachBeat.EVENING_REFLECT
        }

        val sentToday = if (state.moveInvitesOn == today) state.moveInvitesToday else 0
        val longEnoughSince = state.lastMoveInviteAt?.let {
            java.time.Duration.between(it, now.toInstant()).toMinutes() >= MOVE_GAP_MINUTES
        } ?: true
        // At the weekend "working hours" are simply the waking day, minus the first and last hour —
        // a Saturday still has an afternoon that needs getting out of.
        val movingFrom = if (weekend) wrapped(wakeMinutes + 120) else workStartMinutes
        val movingTo = if (weekend) wrapped(sleepMinutes - 180) else workEndMinutes
        if (sentToday < maxMoveInvites &&
            longEnoughSince &&
            UrgePatterns.contains(movingFrom, movingTo, minuteOfDay)
        ) {
            return CoachBeat.MOVE_INVITE
        }
        return null
    }

    private fun wrapped(minutes: Int): Int = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
}
