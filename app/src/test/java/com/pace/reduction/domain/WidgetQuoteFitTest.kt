package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mirrors [com.pace.reduction.data.repository.PaceRepository.fitToWidget]. The repository needs an
 * Android context to construct, so the rule is pinned here against the same constant.
 */
class WidgetQuoteFitTest {
    private val max = 90

    private fun fit(raw: String): String? {
        val cleaned = raw.trim().trim('"').replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return null
        if (cleaned.length <= max) return cleaned
        val firstSentence = cleaned.split(Regex("(?<=[.!?])\\s"), limit = 2).first().trim()
        return firstSentence.takeIf { it.isNotEmpty() && it.length <= max }
    }

    @Test
    fun shortLinesPassThroughUnchanged() {
        assertEquals("Small steps still cover ground.", fit("Small steps still cover ground."))
    }

    @Test
    fun surroundingQuotesAndWhitespaceAreStripped() {
        assertEquals("Begin anywhere. — John Cage", fit("  \"Begin anywhere.  — John Cage\"  "))
    }

    @Test
    fun tooLongFallsBackToTheFirstSentence() {
        val raw = "Begin anywhere. " + "Then keep going for a very long time indeed, ".repeat(3)

        assertEquals("Begin anywhere.", fit(raw))
    }

    @Test
    fun aSingleOverlongSentenceIsRejectedRatherThanClipped() {
        val raw = "x".repeat(200)

        assertNull(fit(raw))
    }

    @Test
    fun blankInputYieldsNothing() {
        assertNull(fit("   "))
        assertNull(fit(""))
    }

    @Test
    fun exactlyAtTheLimitIsKept() {
        val raw = "y".repeat(max)

        assertEquals(raw, fit(raw))
    }
}
