package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybookTest {
    @Test
    fun `plans survive a round trip through storage`() {
        val plan = IfThenPlan("it is 3pm at my desk", "walk to the window and back")
        assertEquals(plan, Playbook.decode(plan.encode()))
    }

    @Test
    fun `broken stored values are skipped`() {
        assertNull(Playbook.decode("no separator here"))
        assertNull(Playbook.decode("\u001Fonly an action"))
    }

    @Test
    fun `sanitising trims, caps and de-duplicates`() {
        val long = "x".repeat(200)
        val plans = Playbook.sanitise(
            listOf(
                IfThenPlan("  after lunch.  ", " make tea "),
                IfThenPlan("After lunch", "Make tea"),
                IfThenPlan(long, "y"),
                IfThenPlan("", "nothing"),
            ) + (1..10).map { IfThenPlan("cue $it", "action $it") },
        )
        assertEquals(Playbook.MAX_PLANS, plans.size)
        assertEquals(IfThenPlan("after lunch", "make tea"), plans[0])
        assertEquals(Playbook.MAX_PART_CHARS, plans[1].cue.length)
    }

    @Test
    fun `suggestions are read from the model and stripped of their own if and then`() {
        val raw = """{"plans":[
            {"if":"If the 3pm slump hits","then":"then I refill my water bottle"},
            {"if":"I finish a meeting","then":"I take the stairs down and back"},
            {"if":"I want a smoke after dinner","then":"I wash up straight away"}
        ]}"""

        val plans = Playbook.parseSuggestions(raw)

        assertEquals(2, plans.size)
        assertEquals("the 3pm slump hits", plans[0].cue)
        assertEquals("I refill my water bottle", plans[0].action)
        assertTrue(plans.none { TriggerWords.contains(it.cue + it.action) })
    }
}
