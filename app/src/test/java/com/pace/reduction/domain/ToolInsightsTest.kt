package com.pace.reduction.domain

import com.pace.reduction.domain.model.UrgeSession
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolInsightsTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")

    private fun session(
        tool: String,
        hoursAgo: Long,
        before: Int? = null,
        after: Int? = null,
        completed: Boolean = true,
    ) = UrgeSession(
        id = "$tool-$hoursAgo",
        startedAt = now.minus(Duration.ofHours(hoursAgo)),
        endedAt = now.minus(Duration.ofHours(hoursAgo)),
        tool = tool,
        urgeBefore = before,
        urgeAfter = after,
        triggerTags = emptySet(),
        note = null,
        completed = completed,
        smokedAfter = null,
        externalRef = null,
    )

    @Test
    fun `rated tools that work rank first`() {
        val sessions = listOf(
            session("TRIVIA", 1),
            session("TRIVIA", 2),
            session("TRIVIA", 3),
            session("BREATHING", 4, before = 4, after = 2),
            session("BREATHING", 5, before = 5, after = 3),
            session("PAUSE", 6),
            session("BLOCKS", 7, completed = false),
        )

        val stats = ToolInsights.calculate(sessions, now)

        assertEquals(listOf("BREATHING", "TRIVIA"), stats.map { it.toolId })
        assertTrue(stats.first().proven)
        assertEquals(2.0, stats.first().averageDrop!!, 0.001)
        assertNull(stats[1].averageDrop)
    }

    @Test
    fun `sessions older than the lookback are ignored`() {
        val stats = ToolInsights.calculate(listOf(session("MEMORY", 24 * 90)), now)
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `recent tools are distinct and newest first`() {
        val recent = ToolInsights.recent(
            listOf(session("BLOCKS", 3), session("TRIVIA", 1), session("BLOCKS", 2), session("MOVE:desk_reset", 4), session("PAUSE", 0)),
        )
        assertEquals(listOf("TRIVIA", "BLOCKS", "MOVE:desk_reset"), recent)
    }

    @Test
    fun `a loud moment gets a calming tool`() {
        val pick = Autopilot.fallback(5, 14 * 60, emptyList(), emptyList(), coachReady = false, seed = 1)
        assertTrue(pick.toolId in setOf(ToolDirectory.BREATHING, ToolDirectory.URGE_SURF))
        assertEquals(AutopilotReason.STRONG_MOMENT, pick.reason)
    }

    @Test
    fun `proven tools are reached for when it is not loud`() {
        val stats = ToolInsights.calculate(
            listOf(session("GROUNDING", 4, 4, 2), session("GROUNDING", 8, 3, 1)),
            now,
        )
        val pick = Autopilot.fallback(2, 14 * 60, stats, emptyList(), coachReady = false, seed = 1)
        assertEquals(ToolDirectory.GROUNDING, pick.toolId)
        assertEquals(AutopilotReason.PROVEN_FOR_YOU, pick.reason)
    }

    @Test
    fun `the offline picker never chooses a tool that needs the coach`() {
        (0L until 50L).forEach { seed ->
            val pick = Autopilot.fallback(null, 20 * 60, emptyList(), listOf("MOVE:walk_reset"), coachReady = false, seed = seed)
            assertTrue(ToolDirectory.byId(pick.toolId)?.needsCoach == false)
        }
    }

    @Test
    fun `the model's pick is validated against the directory`() {
        val good = Autopilot.parse("""{"tool":"EMOJI","message":"Three minutes of emoji detective work. Go!"}""")
        assertNotNull(good)
        assertEquals(ToolDirectory.EMOJI, good!!.toolId)
        assertEquals("Three minutes of emoji detective work. Go!", good.message)

        assertNull(Autopilot.parse("""{"tool":"JUGGLING","message":"Juggle!"}"""))
        assertNull(Autopilot.parse("""{"tool":"STORY","message":"Story time"}""", coachReady = false))
    }
}
