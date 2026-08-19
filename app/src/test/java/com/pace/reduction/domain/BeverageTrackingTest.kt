package com.pace.reduction.domain

import com.pace.reduction.domain.model.BeverageLog
import com.pace.reduction.domain.model.BeverageType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BeverageTrackingTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private val today = LocalDate.of(2026, 8, 19)

    private fun log(
        id: String,
        type: BeverageType,
        instant: String,
        reversed: Boolean = false,
    ) = BeverageLog(
        id = id,
        type = type,
        occurredAt = Instant.parse(instant),
        recordedAt = Instant.parse(instant),
        source = "APP",
        reversedAt = Instant.parse(instant).takeIf { reversed },
    )

    @Test
    fun countsOnlyActiveDrinksFromTheRequestedLocalDay() {
        val logs = listOf(
            log("coffee", BeverageType.COFFEE, "2026-08-19T07:00:00Z"),
            log("wine", BeverageType.ALCOHOL, "2026-08-19T20:00:00Z"),
            log("reversed", BeverageType.COFFEE, "2026-08-19T10:00:00Z", reversed = true),
            // 22:30 UTC is already the following day in Copenhagen in August.
            log("tomorrow", BeverageType.OTHER, "2026-08-19T22:30:00Z"),
        )

        assertEquals(BeverageCounts(coffee = 1, alcohol = 1), BeverageTracker.countsFor(today, zone, logs))
    }
}
