package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgePatternTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 8, 11)

    private var sequence = 0

    private fun log(daysAgo: Long, hour: Int, minute: Int = 0): CigaretteLog {
        val moment = today.minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant()
        return CigaretteLog(
            id = "log-${sequence++}",
            occurredAt = moment,
            recordedAt = moment,
            source = "APP",
            reversedAt = null,
        )
    }

    /** Ten days, four moments each: three inside the given hour band and one spread elsewhere. */
    private fun clustered(startHour: Int, days: Int = 10): List<CigaretteLog> = buildList {
        repeat(days) { day ->
            add(log(day.toLong(), startHour, 10))
            add(log(day.toLong(), startHour, 45))
            add(log(day.toLong(), startHour + 1, 20))
            add(log(day.toLong(), (day % 8) + 6))
        }
    }

    @Test
    fun findsTheStretchTheDayActuallyClustersIn() {
        val pattern = UrgePatterns.analyse(today, zone, clustered(startHour = 15))

        val window = requireNotNull(pattern.window) { "expected a window" }
        // Trimmed to where the moments actually are, not to the band that happened to contain them.
        assertEquals(15 * 60, window.startMinutes)
        assertTrue(
            "expected the band to cover the whole cluster",
            UrgePatterns.contains(window.startMinutes, window.endMinutes, 16 * 60 + 20),
        )
        assertTrue("expected the window to be denser than a flat day", window.lift > 1.5f)
        assertEquals(40, pattern.sampleSize)
        assertEquals(10, pattern.daysCovered)
    }

    @Test
    fun proposesNothingWhenTheDayIsEvenlySpread() {
        // One moment in every other hour, for ten days: forty-plus samples and no pattern in them.
        val logs = buildList {
            repeat(10) { day -> (0..23 step 2).forEach { hour -> add(log(day.toLong(), hour)) } }
        }

        assertNull(UrgePatterns.analyse(today, zone, logs).window)
    }

    @Test
    fun staysSilentUntilThereIsEnoughHistory() {
        // Tightly clustered, but only three days of it — a pattern nobody has earned yet.
        val pattern = UrgePatterns.analyse(today, zone, clustered(startHour = 15, days = 3))

        assertNull(pattern.window)
        assertEquals(3, pattern.daysCovered)
    }

    @Test
    fun ignoresReversedAndOutOfWindowMoments() {
        val stale = log(daysAgo = 200, hour = 15)
        val reversed = clustered(startHour = 15).first().copy(reversedAt = Instant.now())

        val pattern = UrgePatterns.analyse(today, zone, listOf(stale, reversed))

        assertEquals(0, pattern.sampleSize)
        assertNull(pattern.window)
    }

    @Test
    fun reportsTheBusiestHoursEvenWithoutAWindow() {
        val logs = listOf(log(1, 20), log(2, 20), log(3, 20), log(4, 8), log(5, 8), log(6, 13))

        assertEquals(listOf(20, 8, 13), UrgePatterns.analyse(today, zone, logs).busiestHours)
    }

    @Test
    fun containsWrapsPastMidnight() {
        // 22:00 to 01:00 — the case a naive `in start..end` gets exactly backwards.
        assertTrue(UrgePatterns.contains(22 * 60, 60, 23 * 60))
        assertTrue(UrgePatterns.contains(22 * 60, 60, 30))
        assertFalse(UrgePatterns.contains(22 * 60, 60, 12 * 60))
        assertTrue(UrgePatterns.contains(9 * 60, 17 * 60, 12 * 60))
        assertFalse(UrgePatterns.contains(9 * 60, 17 * 60, 18 * 60))
    }

    @Test
    fun findsAWindowThatStraddlesMidnight() {
        val logs = buildList {
            repeat(10) { day ->
                add(log(day.toLong(), 23, 10))
                add(log(day.toLong(), 23, 40))
                add(log(day.toLong(), 0, 20))
                add(log(day.toLong(), (day % 8) + 9))
            }
        }

        val window = UrgePatterns.analyse(today, zone, logs).window
        assertNotNull("a late-night cluster must not be cut in half by midnight", window)
        assertTrue(UrgePatterns.contains(window!!.startMinutes, window.endMinutes, 23 * 60 + 30))
    }
}
