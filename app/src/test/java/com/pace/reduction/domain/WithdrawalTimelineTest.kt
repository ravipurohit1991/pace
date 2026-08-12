package com.pace.reduction.domain

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WithdrawalTimelineTest {

    private fun at(hours: Long) = WithdrawalTimeline.calculate(Duration.ofHours(hours))

    @Test
    fun namesTheStageForEachPartOfTheCurve() {
        assertEquals("settling", at(1).current?.id)
        assertEquals("climbing", at(12).current?.id)
        assertEquals("peak", at(48).current?.id)
        assertEquals("turning", at(96).current?.id)
        assertEquals("easing", at(10 * 24).current?.id)
        assertEquals("fading", at(20 * 24).current?.id)
        assertEquals("clear", at(40 * 24).current?.id)
    }

    @Test
    fun peakEndsAtSeventyTwoHours() {
        assertFalse(at(71).peakPassed)
        assertEquals(1, at(71).hoursToPeakEnd)
        assertTrue(at(72).peakPassed)
        assertEquals(0, at(200).hoursToPeakEnd)
    }

    @Test
    fun retiresItselfAfterAMonth() {
        assertTrue(at(29 * 24).relevant)
        assertFalse(at(30 * 24).relevant)
    }

    @Test
    fun aFreshStretchHasReachedOnlyTheFirstStage() {
        val phases = at(2).phases

        assertEquals(listOf(true, false, false, false, false, false, false), phases.map { it.reached })
    }

    @Test
    fun theCurveRisesToThePeakAndFallsAway() {
        val curve = WithdrawalTimeline.curve(48)
        val peakIndex = curve.indexOf(curve.maxOrNull()!!)

        assertEquals(48, curve.size)
        // The summit is inside the second and third day, not at either end of the week.
        assertTrue("peak sits too early: $peakIndex", peakIndex > 4)
        assertTrue("peak sits too late: $peakIndex", peakIndex < curve.size / 2)
        assertTrue("the curve must come back down", curve.last() < curve[peakIndex])
        assertTrue("the curve must climb into it", curve.first() < curve[peakIndex])
    }

    @Test
    fun intensityStartsAtNothingAndIsBounded() {
        assertEquals(0f, WithdrawalTimeline.intensityAt(0f), 0.001f)
        WithdrawalTimeline.curve(64).forEach {
            assertTrue("intensity out of range: $it", it in 0f..1f)
        }
    }

    @Test
    fun everyStageHasCopyToShow() {
        // The card renders `current`, so no reachable duration may leave it null.
        listOf(0L, 3L, 5L, 23L, 24L, 71L, 72L, 167L, 168L, 400L, 719L, 720L, 5_000L).forEach { hours ->
            assertNotNull("no stage at $hours hours", at(hours).current)
        }
    }
}
