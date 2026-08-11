package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathPatternTest {

    @Test
    fun `box breathing walks all four phases in order`() {
        val box = BreathPattern.BOX
        assertEquals(BreathPhase.INHALE, box.tickAt(0L).phase)
        assertEquals(BreathPhase.HOLD_IN, box.tickAt(4_500L).phase)
        assertEquals(BreathPhase.EXHALE, box.tickAt(8_500L).phase)
        assertEquals(BreathPhase.HOLD_OUT, box.tickAt(12_500L).phase)
    }

    @Test
    fun `a phase of zero seconds is skipped rather than flashed`() {
        // 4-7-8 has no rest at the bottom, so the cycle must restart straight into the inhale.
        val relaxing = BreathPattern.RELAXING
        assertEquals(BreathPhase.EXHALE, relaxing.tickAt(18_900L).phase)
        assertEquals(BreathPhase.INHALE, relaxing.tickAt(19_100L).phase)
    }

    @Test
    fun `the orb is full at the top of the breath and empty at the bottom`() {
        val box = BreathPattern.BOX
        assertEquals(0f, box.tickAt(0L).scale, 0.01f)
        assertEquals(1f, box.tickAt(4_000L).scale, 0.01f)
        assertEquals(1f, box.tickAt(7_900L).scale, 0.01f)
        assertEquals(0f, box.tickAt(12_100L).scale, 0.01f)
    }

    @Test
    fun `the countdown reads the whole of its first second`() {
        val box = BreathPattern.BOX
        assertEquals(4, box.tickAt(0L).secondsLeftInPhase)
        assertEquals(4, box.tickAt(500L).secondsLeftInPhase)
        assertEquals(1, box.tickAt(3_500L).secondsLeftInPhase)
    }

    @Test
    fun `cycles are counted so a session can end on a whole breath`() {
        val box = BreathPattern.BOX
        assertEquals(0, box.tickAt(15_999L).completedCycles)
        assertEquals(1, box.tickAt(16_000L).completedCycles)
        assertEquals(3, box.tickAt(48_000L).completedCycles)
    }

    @Test
    fun `a two minute session is a sensible number of breaths for every pattern`() {
        BreathPattern.all.forEach { pattern ->
            val cycles = pattern.cyclesFor(120)
            assertTrue(pattern.id, cycles >= 3)
            assertTrue(pattern.id, cycles * pattern.cycleSeconds >= 120)
            // And never so many that "two minutes" becomes four.
            assertTrue(pattern.id, cycles * pattern.cycleSeconds < 120 + pattern.cycleSeconds)
        }
    }

    @Test
    fun `an unknown pattern id falls back rather than crashing`() {
        assertEquals(BreathPattern.BOX, BreathPattern.byId("NOPE"))
    }
}
