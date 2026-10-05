package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachActionsTest {
    @Test
    fun `a trailing tag becomes an action and disappears from the text`() {
        val reply = "Oof, let's slow it right down together. Two minutes, eyes on the circle. [[do:BREATHING]]"

        assertEquals(ToolDirectory.BREATHING, CoachActions.extract(reply)?.id)
        assertEquals("Oof, let's slow it right down together. Two minutes, eyes on the circle.", CoachActions.strip(reply))
    }

    @Test
    fun `tags are matched loosely on case and spacing`() {
        assertEquals(ToolDirectory.TRIVIA, CoachActions.extract("Quiz time [[ DO : trivia ]]")?.id)
        assertEquals("MOVE:walk_reset", CoachActions.extract("Out you go [[do:MOVE:walk_reset]]")?.id)
    }

    @Test
    fun `an invented id is stripped but opens nothing`() {
        val reply = "Let's do something fun [[do:SKYDIVING]]"

        assertNull(CoachActions.extract(reply))
        assertEquals("Let's do something fun", CoachActions.strip(reply))
    }

    @Test
    fun `half a tag still arriving is hidden while streaming`() {
        assertEquals("Try this", CoachActions.strip("Try this [[do:BRE"))
        assertEquals("Try this", CoachActions.strip("Try this [["))
        assertEquals("Try this", CoachActions.strip("Try this ["))
        assertEquals("Try this", CoachActions.strip("Try this [[do:BREATHING]"))
    }

    @Test
    fun `tools that need the coach are not offered when it is off`() {
        assertNull(CoachActions.extract("[[do:STORY]]", coachReady = false))
        assertEquals(ToolDirectory.STORY, CoachActions.extract("[[do:STORY]]", coachReady = true)?.id)
    }

    @Test
    fun `plain replies pass through untouched`() {
        val reply = "Nothing to open here, just chatting."
        assertNull(CoachActions.extract(reply))
        assertEquals(reply, CoachActions.strip(reply))
    }

    @Test
    fun `the instruction lists real ids and never names the substance`() {
        val instruction = CoachActions.instruction()
        ToolDirectory.all.forEach { assertTrue(instruction.contains(it.id)) }
        assertFalse(TriggerWords.contains(instruction))
    }

    @Test
    fun `chat requests carry the action menu only when asked to`() {
        val withActions = CoachPrompt.messages(CoachTask.CHAT, null, emptyList(), allowActions = true).first().content
        val without = CoachPrompt.messages(CoachTask.CHAT, null, emptyList()).first().content

        assertTrue(withActions.contains("[[do:ID]]"))
        assertFalse(without.contains("[[do:ID]]"))
        // The rule about never naming it stays the last word either way.
        assertTrue(withActions.trimEnd().endsWith("never the substance."))
    }
}
