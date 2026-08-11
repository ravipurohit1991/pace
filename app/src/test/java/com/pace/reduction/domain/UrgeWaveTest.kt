package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgeWaveTest {

    @Test
    fun `the wave starts flat and crests early`() {
        assertEquals(0f, UrgeWave.height(0f), 0.01f)
        assertTrue(UrgeWave.height(0.32f) > 0.9f)
    }

    @Test
    fun `the second half is lower than the crest, which is the whole promise`() {
        val crest = UrgeWave.height(0.32f)
        listOf(0.5f, 0.7f, 0.9f, 1f).forEach { progress ->
            assertTrue("at $progress", UrgeWave.height(progress) < crest)
        }
    }

    @Test
    fun `it recedes to a tail rather than to nothing`() {
        val end = UrgeWave.height(1f)
        assertTrue(end > 0.1f)
        assertTrue(end < 0.3f)
    }

    @Test
    fun `height never leaves the unit range, including past the ends`() {
        var progress = -0.5f
        while (progress <= 1.5f) {
            val height = UrgeWave.height(progress)
            assertTrue("at $progress", height in 0f..1f)
            progress += 0.01f
        }
    }

    @Test
    fun `stages are spread over the session and stay in bounds`() {
        assertEquals(0, UrgeWave.stageIndex(0f))
        assertEquals(UrgeWave.STAGE_COUNT - 1, UrgeWave.stageIndex(1f))
        assertEquals(UrgeWave.STAGE_COUNT - 1, UrgeWave.stageIndex(2f))
        assertEquals(0, UrgeWave.stageIndex(-1f))
        assertEquals(3, UrgeWave.stageIndex(0.5f))
    }
}
