package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widths are the real ones: two of the sizes `PaceWidget` declares, less the 14dp of padding it
 * keeps on each side.
 */
class WidgetStatFitTest {
    private val free = "4h free"
    private val steps = "3.4k steps"
    private val saved = "12 DKK saved"
    private val streak = "3-day streak"

    private val medium = 180.0 - 28
    private val wide = 300.0 - 28

    @Test
    fun aMediumWidgetKeepsTheWalkingAndDropsTheTail() {
        val fitted = WidgetStatFit.fit(listOf(free, steps, saved, streak), medium)

        assertEquals(listOf(free, steps), fitted)
    }

    @Test
    fun aWideWidgetShowsMore() {
        val fitted = WidgetStatFit.fit(listOf(free, steps, saved, streak), wide)

        assertEquals(listOf(free, steps, saved), fitted)
    }

    @Test
    fun everythingFitsWhenThereIsRoomForIt() {
        val fitted = WidgetStatFit.fit(listOf(free, steps, saved, streak), 500.0)

        assertEquals(listOf(free, steps, saved, streak), fitted)
    }

    /** A pill wider than the whole row is left out rather than clipped to the edge. */
    @Test
    fun aRowTooNarrowForEvenOnePillShowsNothing() {
        assertEquals(emptyList<String>(), WidgetStatFit.fit(listOf(streak), 40.0))
    }

    @Test
    fun theTailIsNotReorderedToFillTheGap() {
        // "12 DKK saved" would fit in what is left after the first two, but taking it ahead of the
        // pill before it would shuffle the row every time a figure changed width.
        val fitted = WidgetStatFit.fit(listOf(free, "a-very-long-pill-label-indeed", saved), medium)

        assertEquals(listOf(free), fitted)
    }

    @Test
    fun fittedPillsNeverExceedTheRow() {
        val fitted = WidgetStatFit.fit(listOf(free, steps, saved, streak), medium)
        val used = fitted.sumOf { WidgetStatFit.widthOf(it) } + 6.0 * (fitted.size - 1)

        assertTrue("$used dp used of $medium", used <= medium)
    }

    @Test
    fun noLabelsFitNothing() {
        assertEquals(emptyList<String>(), WidgetStatFit.fit(emptyList(), wide))
    }
}
