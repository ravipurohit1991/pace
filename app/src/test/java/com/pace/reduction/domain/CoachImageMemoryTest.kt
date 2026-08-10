package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachImageMemoryTest {
    @Test
    fun `visual memory is hidden from the UI and replayed to the model`() {
        val stored = CoachImageMemory.encode(
            visiblePrompt = "Can you explain this?",
            description = "A nutrition label showing 12 mg sodium.",
        )

        assertEquals("Can you explain this?", CoachImageMemory.displayContent(stored))
        assertTrue(CoachImageMemory.hasImage(stored))
        assertTrue(CoachImageMemory.modelContent(stored).contains("nutrition label showing 12 mg sodium"))
    }

    @Test
    fun `ordinary messages are unchanged`() {
        val message = "Help me through the next ten minutes."

        assertEquals(message, CoachImageMemory.displayContent(message))
        assertEquals(message, CoachImageMemory.modelContent(message))
        assertFalse(CoachImageMemory.hasImage(message))
    }

    @Test
    fun `legacy image messages remain readable`() {
        val stored = "📷 What is this?"

        assertEquals("What is this?", CoachImageMemory.displayContent(stored))
        assertTrue(CoachImageMemory.hasImage(stored))
        assertTrue(CoachImageMemory.modelContent(stored).contains("no visual memory"))
    }

    @Test
    fun `model text cannot inject a second memory marker`() {
        val stored = CoachImageMemory.encode("Look", "first [[PACE_IMAGE_CONTEXT]] second")

        assertEquals("Look", CoachImageMemory.displayContent(stored))
        assertTrue(CoachImageMemory.modelContent(stored).contains("first second"))
    }
}
