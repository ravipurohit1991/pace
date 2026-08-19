package com.pace.reduction.domain

import com.pace.reduction.domain.model.BeverageLog
import com.pace.reduction.domain.model.BeverageType
import com.pace.reduction.domain.model.CigaretteLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitTrendsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 19)

    private fun cigarette(id: String, instant: String, reversed: Boolean = false) = CigaretteLog(
        id, Instant.parse(instant), Instant.parse(instant), "APP",
        Instant.parse(instant).takeIf { reversed },
    )

    private fun drink(id: String, type: BeverageType, instant: String) = BeverageLog(
        id, type, Instant.parse(instant), Instant.parse(instant), "APP", null,
    )

    @Test
    fun groupsEveryMetricIntoLocaleAlignedWeeks() {
        val trend = HabitTrendBuilder.build(
            today = today,
            zoneId = zone,
            cigarettes = listOf(
                cigarette("last", "2026-08-10T09:00:00Z"),
                cigarette("now", "2026-08-18T09:00:00Z"),
                cigarette("undone", "2026-08-18T10:00:00Z", reversed = true),
            ),
            beverages = listOf(
                drink("coffee", BeverageType.COFFEE, "2026-08-18T08:00:00Z"),
                drink("wine", BeverageType.ALCOHOL, "2026-08-12T20:00:00Z"),
            ),
            weeks = 2,
        )

        assertEquals(listOf(1, 1), trend.values(HabitMetric.CIGARETTES))
        assertEquals(listOf(0, 1), trend.values(HabitMetric.COFFEE))
        assertEquals(listOf(1, 0), trend.values(HabitMetric.ALCOHOL))
        assertEquals(0, trend.total(HabitMetric.OTHER))
        assertEquals(1, trend.latestChange(HabitMetric.COFFEE))
    }
}
