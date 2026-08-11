package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveCatalogueTest {
    private val wake = 7 * 60
    private val sleep = 22 * 60 + 30

    @Test
    fun everySessionIsShortEnoughToAgreeToAndLongEnoughToCount() {
        MoveCatalogue.all.forEach { session ->
            assertTrue("${session.id} has no steps", session.steps.isNotEmpty())
            assertTrue("${session.id} is too short", session.totalSeconds >= 120)
            assertTrue("${session.id} is too long", session.minutes <= 15)
            session.steps.forEach { step ->
                assertTrue("${session.id}/${step.id} has no duration", step.seconds > 0)
            }
        }
    }

    @Test
    fun theShelfIsOrderedShortestFirst() {
        val minutes = MoveCatalogue.shelf.map { it.minutes }

        assertEquals(minutes.sorted(), minutes)
        assertEquals(MoveCatalogue.all.size, MoveCatalogue.shelf.size)
    }

    @Test
    fun onlyWalkingAndRunningClaimAPedometerTarget() {
        MoveCatalogue.all.forEach { session ->
            val movesThroughSpace = session.kind == MoveKind.WALK || session.kind == MoveKind.RUN
            assertEquals(
                "${session.id} disagrees with itself about being verifiable",
                movesThroughSpace,
                session.verifiedByPedometer,
            )
        }
    }

    @Test
    fun byIdFallsBackRatherThanThrowing() {
        assertEquals(MoveCatalogue.WALK_RESET, MoveCatalogue.byId(MoveCatalogue.WALK_RESET).id)
        assertTrue(MoveCatalogue.byId("nonsense").steps.isNotEmpty())
    }

    @Test
    fun suggestsTheFlowThatFitsTheHour() {
        val morning = MoveCatalogue.suggestFor(7 * 60 + 30, wake, sleep)
        val afternoon = MoveCatalogue.suggestFor(15 * 60, wake, sleep)
        val bedtime = MoveCatalogue.suggestFor(21 * 60 + 30, wake, sleep)

        assertEquals(MoveCatalogue.YOGA_MORNING, morning.id)
        assertEquals(MoveCatalogue.WALK_RESET, afternoon.id)
        assertEquals(MoveCatalogue.YOGA_WIND_DOWN, bedtime.id)
    }

    @Test
    fun offersALongerWalkWhenTheDaysWalkingIsBehind() {
        val suggestion = MoveCatalogue.suggestFor(
            minuteOfDay = 11 * 60,
            wakeMinutes = wake,
            sleepMinutes = sleep,
            stepsToday = 500,
            averageSteps = 6_000.0,
        )

        assertEquals(MoveCatalogue.WALK_LONG, suggestion.id)
    }

    @Test
    fun tickWalksThroughTheStepsInOrder() {
        val session = MoveCatalogue.byId(MoveCatalogue.DESK_RESET)

        assertEquals(0, session.tickAt(0).index)
        assertEquals(session.steps[0].seconds, session.tickAt(0).secondsLeftInStep)
        // One second before the first step ends is still the first step.
        assertEquals(0, session.tickAt((session.steps[0].seconds - 1) * 1_000L).index)
        assertEquals(1, session.tickAt(session.steps[0].seconds * 1_000L).index)
    }

    @Test
    fun tickFinishesExactlyOnceAtTheEnd() {
        val session = MoveCatalogue.byId(MoveCatalogue.DESK_RESET)
        val total = session.totalSeconds * 1_000L

        assertFalse(session.tickAt(total - 1_000L).finished)
        assertTrue(session.tickAt(total).finished)
        assertTrue(session.tickAt(total + 60_000L).finished)
        assertEquals(1f, session.tickAt(total).sessionProgress, 0.001f)
    }

    @Test
    fun tickSurvivesNonsenseClocks() {
        val session = MoveCatalogue.byId(MoveCatalogue.CIRCUIT)

        assertEquals(0, session.tickAt(-5_000L).index)
        assertFalse(session.tickAt(-5_000L).finished)
    }

    @Test
    fun sessionProgressOnlyMovesForward() {
        val session = MoveCatalogue.byId(MoveCatalogue.PUSHUP_LADDER)
        var previous = -1f

        (0..session.totalSeconds).forEach { second ->
            val progress = session.tickAt(second * 1_000L).sessionProgress
            assertTrue("progress went backwards at ${second}s", progress >= previous)
            previous = progress
        }
    }
}
