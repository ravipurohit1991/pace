package com.pace.reduction.domain

import com.pace.reduction.domain.model.BeverageLog
import com.pace.reduction.domain.model.BeverageType
import java.time.LocalDate
import java.time.ZoneId

data class BeverageCounts(
    val coffee: Int = 0,
    val alcohol: Int = 0,
    val other: Int = 0,
) {
    val total: Int get() = coffee + alcohol + other

    operator fun get(type: BeverageType): Int = when (type) {
        BeverageType.COFFEE -> coffee
        BeverageType.ALCOHOL -> alcohol
        BeverageType.OTHER -> other
    }
}

/** Keeps drink counts local to the user's day, including across non-UTC time zones. */
object BeverageTracker {
    fun countsFor(date: LocalDate, zoneId: ZoneId, logs: List<BeverageLog>): BeverageCounts {
        val counts = logs.asSequence()
            .filter { it.reversedAt == null && it.occurredAt.atZone(zoneId).toLocalDate() == date }
            .groupingBy(BeverageLog::type)
            .eachCount()
        return BeverageCounts(
            coffee = counts[BeverageType.COFFEE] ?: 0,
            alcohol = counts[BeverageType.ALCOHOL] ?: 0,
            other = counts[BeverageType.OTHER] ?: 0,
        )
    }
}
