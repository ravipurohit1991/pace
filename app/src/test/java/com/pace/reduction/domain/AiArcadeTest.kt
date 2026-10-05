package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiArcadeTest {
    @Test
    fun `trivia is parsed and invalid questions are dropped`() {
        val raw = """
            ```json
            {"questions":[
              {"question":"Which planet is red?","options":["Mars","Venus","Earth","Pluto"],"answer_index":0,"fact":"Iron oxide."},
              {"question":"Broken index","options":["A","B"],"answer_index":7,"fact":""},
              {"question":"Duplicate options","options":["A","a","B"],"answer_index":0},
              {"question":"Who invented the cigarette lighter?","options":["X","Y","Z"],"answer_index":1}
            ]}
            ```
        """.trimIndent()

        val questions = ArcadeParser.trivia(raw)

        assertEquals(1, questions.size)
        assertEquals("Mars", questions[0].options[questions[0].answerIndex])
        assertEquals("Iron oxide.", questions[0].fact)
    }

    @Test
    fun `garbage yields an empty round rather than a crash`() {
        assertTrue(ArcadeParser.trivia("not json at all").isEmpty())
        assertTrue(ArcadeParser.emoji("{\"puzzles\": 3}").isEmpty())
        assertNull(ArcadeParser.story(""))
    }

    @Test
    fun `emoji clues that spell the answer in letters are rejected`() {
        val raw = """{"puzzles":[
            {"emojis":"🦁👑","answer":"The Lion King","hint":"A cub","category":"Film"},
            {"emojis":"JAWS🦈","answer":"Jaws","hint":"","category":"Film"}
        ]}"""

        val puzzles = ArcadeParser.emoji(raw)

        assertEquals(listOf("The Lion King"), puzzles.map { it.answer })
    }

    @Test
    fun `a story beat without choices is an ending`() {
        val beat = ArcadeParser.story("""{"scene":"The vault door swings open.","choices":[],"ending":false}""")
        assertNotNull(beat)
        assertTrue(beat!!.ending)

        val middle = ArcadeParser.story(
            """{"scene":"A cat in a tiny hat blocks the corridor.","choices":["Bow","Offer tuna","Bow","Sneak past"],"ending":false}""",
        )
        assertEquals(listOf("Bow", "Offer tuna", "Sneak past"), middle!!.choices)
        assertFalse(middle.ending)
    }

    @Test
    fun `answers match forgivingly but not wrongly`() {
        assertTrue(AnswerMatcher.matches("lion king", "The Lion King"))
        assertTrue(AnswerMatcher.matches("Spiderman", "Spider-Man"))
        assertTrue(AnswerMatcher.matches("the lion kng", "The Lion King"))
        assertTrue(AnswerMatcher.matches("Pokémon", "pokemon"))
        assertTrue(AnswerMatcher.matches("raining cats & dogs", "Raining cats and dogs"))
        assertFalse(AnswerMatcher.matches("Jaws 2", "Up"))
        assertFalse(AnswerMatcher.matches("Cat", "Car"))
        assertFalse(AnswerMatcher.matches("", "Frozen"))
    }

    @Test
    fun `offline banks deal full, valid, varied rounds`() {
        val first = ArcadeOffline.triviaRound(seed = 1)
        val second = ArcadeOffline.triviaRound(seed = 2)
        assertEquals(ArcadeParser.ROUND_SIZE, first.size)
        assertTrue(first.all { it.answerIndex in it.options.indices })
        assertTrue(first != second)
        assertEquals(ArcadeParser.ROUND_SIZE, ArcadeOffline.emojiRound(seed = 3).size)
    }

    @Test
    fun `nothing in the offline banks names the substance`() {
        ArcadeOffline.trivia.forEach { question ->
            assertFalse(question.question, TriggerWords.contains(question.question + question.options + question.fact))
        }
        ArcadeOffline.emoji.forEach { puzzle ->
            assertFalse(puzzle.answer, TriggerWords.contains(puzzle.answer + puzzle.hint))
        }
    }

    @Test
    fun `game prompts keep the never-name-it rule last`() {
        listOf(CoachTask.STORY, CoachTask.TRIVIA, CoachTask.EMOJI, CoachTask.AUTOPILOT, CoachTask.PLAYBOOK).forEach { task ->
            val messages = CoachPrompt.messages(task, null, emptyList(), kickoffOverride = "Go.")
            assertTrue(messages.first().content.trimEnd().endsWith("never the substance."))
            assertEquals("Go.", messages.last().content)
        }
    }
}
