package com.pace.reduction.data

import com.pace.reduction.data.seed.ProvisioningSeeder
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningSeederTest {
    private val date = LocalDate.of(2026, 8, 3)
    private val wake = LocalTime.of(7, 0)

    @Test
    fun noCountProducesNoEntries() {
        assertTrue(ProvisioningSeeder.timesFor(0, date, null, wake).isEmpty())
    }

    @Test
    fun todaySpreadEndsExactlyOnTheLastCigarette() {
        val last = LocalTime.of(14, 42)

        val times = ProvisioningSeeder.timesFor(5, date, last, wake)

        assertEquals(5, times.size)
        assertEquals(last, times.last())
        assertEquals(wake, times.first())
    }

    @Test
    fun spreadIsChronologicalAndEvenlyPaced() {
        val times = ProvisioningSeeder.timesFor(5, date, LocalTime.of(14, 42), wake)

        val gaps = times.zipWithNext { a, b -> b.toSecondOfDay() / 60 - a.toSecondOfDay() / 60 }
        assertTrue("times must ascend", gaps.all { it > 0 })
        // Even pacing: no two gaps differ by more than a rounding minute.
        assertTrue("gaps should be even, were $gaps", gaps.max() - gaps.min() <= 1)
    }

    @Test
    fun aDayWithoutAKnownLastCigaretteRunsToEvening() {
        val times = ProvisioningSeeder.timesFor(7, date, null, wake)

        assertEquals(7, times.size)
        assertEquals(wake, times.first())
        assertEquals(LocalTime.of(22, 0), times.last())
    }

    @Test
    fun singleCigaretteLandsOnTheKnownTime() {
        val times = ProvisioningSeeder.timesFor(1, date, LocalTime.of(14, 42), wake)

        assertEquals(listOf(LocalTime.of(14, 42)), times)
    }

    @Test
    fun lastTimeBeforeWakeDoesNotProduceNegativeSpacing() {
        val times = ProvisioningSeeder.timesFor(3, date, LocalTime.of(5, 0), wake)

        assertEquals(3, times.size)
        assertTrue(times.all { it == LocalTime.of(5, 0) })
    }
}
