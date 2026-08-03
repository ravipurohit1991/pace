package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeCatalogueTest {
    @Test
    fun catalogueIsLargeEnoughToRewardSmallWins() {
        assertTrue("expected hundreds of badges, got ${BadgeCatalogue.size}", BadgeCatalogue.size >= 200)
    }

    @Test
    fun badgeIdsAreUnique() {
        val ids = BadgeCatalogue.all.map { it.id }
        assertEquals(ids.distinct().size, ids.size)
    }

    @Test
    fun everyFamilyStartsWithinReachOfADayOne() {
        // The first tier of each family must be attainable early, otherwise a new user sees nothing.
        BadgeFamily.entries.forEach { family ->
            val first = BadgeCatalogue.forFamily(family).first()
            assertTrue(
                "${family.id} starts at ${first.threshold}, too far for a first session",
                first.threshold <= 30L,
            )
        }
    }

    @Test
    fun thresholdsAscendWithinEachFamily() {
        BadgeFamily.entries.forEach { family ->
            val thresholds = BadgeCatalogue.forFamily(family).map { it.threshold }
            assertEquals("${family.id} must ascend", thresholds.sorted(), thresholds)
        }
    }

    @Test
    fun earnedIncludesEveryTierAtOrBelowTheTotal() {
        val earned = BadgeCatalogue.earned(mapOf(BadgeFamily.CLEAN_HOURS to 12L))
            .filter { it.family == BadgeFamily.CLEAN_HOURS }

        assertEquals(listOf(1L, 2, 3, 4, 6, 8, 10, 12), earned.map { it.threshold })
    }

    @Test
    fun zeroTotalsEarnNothing() {
        assertTrue(BadgeCatalogue.earned(emptyMap()).isEmpty())
    }

    @Test
    fun nextUpPointsAtTheFirstUnearnedTier() {
        val next = BadgeCatalogue.nextUp(mapOf(BadgeFamily.CLEAN_HOURS to 12L))
            .first { it.first.family == BadgeFamily.CLEAN_HOURS }

        assertEquals(18L, next.first.threshold)
        assertEquals(12L, next.second)
    }

    @Test
    fun definitionLookupResolvesGeneratedIds() {
        assertNotNull(BadgeCatalogue.definition("clean_hours_24"))
        assertNull(BadgeCatalogue.definition("clean_hours_25"))
    }
}
