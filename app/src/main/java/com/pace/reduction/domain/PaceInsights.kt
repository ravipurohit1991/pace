package com.pace.reduction.domain

import com.pace.reduction.domain.model.BeverageLog
import com.pace.reduction.domain.model.BeverageType
import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.DailyPlanSnapshot
import com.pace.reduction.domain.model.UrgeSession
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class InsightDay(
    val date: LocalDate,
    val cigarettes: Int,
    val recorded: Boolean,
    val ceiling: Int?,
    val baseline: Int?,
    val coffee: Int,
    val alcohol: Int,
    val otherDrinks: Int,
    val checkIns: Int,
) {
    val onPlan: Boolean? get() = ceiling?.let { cigarettes <= it }
    val avoided: Int? get() = baseline?.let { (it - cigarettes).coerceAtLeast(0) }
}

data class ToolInsight(val tool: String, val completed: Int, val rated: Int, val averageDrop: Double?)
data class TriggerInsight(val tag: String, val count: Int)
data class DrinkAssociation(val type: BeverageType, val followingLogs: Int, val totalLogs: Int)

/** All ranges end yesterday. Partial days never masquerade as a reduction. */
data class PaceInsights(
    val days: List<InsightDay>,
    val previousRecordedDays: Int,
    val average: Double?,
    val previousAverage: Double?,
    val changePercent: Double?,
    val medianGapMinutes: Long?,
    val longestDaytimeGapMinutes: Long?,
    val gapsMeasured: Int,
    val heatmap: List<List<Int>>,
    val triggers: List<TriggerInsight>,
    val taggedCheckIns: Int,
    val tools: List<ToolInsight>,
    val drinkAssociations: List<DrinkAssociation>,
) {
    val recordedDays: Int get() = days.count { it.recorded }
    val total: Int get() = days.sumOf { it.cigarettes }
    val plannedDays: Int get() = days.count { it.ceiling != null }
    val onPlanDays: Int get() = days.count { it.onPlan == true }
    val avoided: Int get() = days.sumOf { it.avoided ?: 0 }
    val completedTools: Int get() = tools.sumOf { it.completed }

    /** Unknown cigarette counts and missing plans remain empty in exports, never fabricated zeroes. */
    fun toCsv(): String = buildString {
        append("date,cigarettes,recorded,ceiling,baseline,coffee,alcohol,other_drinks,check_ins,on_plan\r\n")
        days.forEach { day ->
            append(listOf(
                day.date, if (day.recorded) day.cigarettes else "", day.recorded,
                day.ceiling ?: "", day.baseline ?: "", day.coffee, day.alcohol,
                day.otherDrinks, day.checkIns, day.onPlan ?: "",
            ).joinToString(","))
            append("\r\n")
        }
    }
}

object PaceInsightsCalculator {
    fun calculate(
        now: Instant,
        zoneId: ZoneId,
        logs: List<CigaretteLog>,
        beverages: List<BeverageLog>,
        sessions: List<UrgeSession>,
        snapshots: List<DailyPlanSnapshot>,
        rangeDays: Int = 7,
    ): PaceInsights {
        require(rangeDays in 1..90)
        val today = now.atZone(zoneId).toLocalDate()
        val start = today.minusDays(rangeDays.toLong())
        val previousStart = start.minusDays(rangeDays.toLong())
        val active = logs.filter { it.reversedAt == null && it.occurredAt <= now }
        val byDate = active.groupBy { it.occurredAt.atZone(zoneId).toLocalDate() }
        val plans = snapshots.associateBy { it.localDate }
        val drinksByDate = beverages.filter { it.reversedAt == null && it.occurredAt <= now }
            .groupBy { it.occurredAt.atZone(zoneId).toLocalDate() }
        val sessionByDate = sessions.filter { it.startedAt <= now }
            .groupBy { it.startedAt.atZone(zoneId).toLocalDate() }
        fun day(date: LocalDate): InsightDay {
            val plan = plans[date]
            val drinks = drinksByDate[date].orEmpty()
            return InsightDay(
                date, byDate[date].orEmpty().size, plan != null || byDate.containsKey(date),
                plan?.ceiling, plan?.baseline,
                drinks.count { it.type == BeverageType.COFFEE },
                drinks.count { it.type == BeverageType.ALCOHOL },
                drinks.count { it.type == BeverageType.OTHER },
                sessionByDate[date].orEmpty().size,
            )
        }
        val days = (0 until rangeDays).map { day(start.plusDays(it.toLong())) }
        val previous = (0 until rangeDays).map { day(previousStart.plusDays(it.toLong())) }
            .filter { it.recorded }
        val recorded = days.filter { it.recorded }
        val average = recorded.takeIf { it.isNotEmpty() }?.map { it.cigarettes }?.average()
        val priorAverage = previous.takeIf { it.isNotEmpty() }?.map { it.cigarettes }?.average()
        val selectedLogs = days.flatMap { byDate[it.date].orEmpty() }
        // Pair within each local calendar day, so sleep and untracked days cannot inflate gaps.
        val gaps = days.flatMap { day ->
            byDate[day.date].orEmpty().sortedBy { it.occurredAt }.zipWithNext { a, b ->
                Duration.between(a.occurredAt, b.occurredAt).toMinutes().coerceAtLeast(0)
            }
        }.sorted()
        val median = if (gaps.isEmpty()) null else if (gaps.size % 2 == 1) gaps[gaps.size / 2]
            else (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2
        val heatmap = List(7) { MutableList(6) { 0 } }
        selectedLogs.forEach {
            val local = it.occurredAt.atZone(zoneId)
            heatmap[local.dayOfWeek.value - 1][local.hour / 4]++
        }
        val selectedSessions = days.flatMap { sessionByDate[it.date].orEmpty() }
        val tagged = selectedSessions.filter { it.triggerTags.isNotEmpty() }
        val triggers = tagged.flatMap { it.triggerTags }.groupingBy { it }.eachCount()
            .map { TriggerInsight(it.key, it.value) }
            .sortedWith(compareByDescending<TriggerInsight> { it.count }.thenBy { it.tag })
        val tools = selectedSessions.filter {
            it.completed && it.tool != "CHECK_IN" && it.endedAt != null && it.endedAt < today.atStartOfDay(zoneId).toInstant()
        }.groupBy { it.tool }.map { (tool, entries) ->
            val rated = entries.filter { it.urgeBefore in 1..5 && it.urgeAfter in 1..5 }
            ToolInsight(tool, entries.size, rated.size,
                rated.takeIf { it.isNotEmpty() }?.map { it.urgeBefore!! - it.urgeAfter!! }?.average())
        }.sortedWith(compareByDescending<ToolInsight> { it.completed }.thenBy { it.tool })
        // A drink just before the range begins can still precede a log just inside it.
        val drinkStart = start.atStartOfDay(zoneId).toInstant().minusSeconds(3600)
        val drinkEnd = today.atStartOfDay(zoneId).toInstant()
        val selectedDrinks = beverages.filter {
            it.reversedAt == null && it.occurredAt >= drinkStart && it.occurredAt < drinkEnd
        }
        val associations = listOf(BeverageType.COFFEE, BeverageType.ALCOHOL).map { type ->
            val moments = selectedDrinks.filter { it.type == type }.map { it.occurredAt }.sorted()
            // Binary search keeps a long history from making this a logs × drinks scan.
            val following = selectedLogs.count { log ->
                val index = moments.binarySearch(log.occurredAt).let { if (it >= 0) it else -it - 2 }
                index >= 0 && Duration.between(moments[index], log.occurredAt) < Duration.ofHours(1)
            }
            DrinkAssociation(type, following, selectedLogs.size)
        }
        return PaceInsights(
            days, previous.size, average, priorAverage,
            if (recorded.size >= 3 && previous.size >= 3 && priorAverage != null && priorAverage > 0)
                ((average!! - priorAverage) / priorAverage) * 100 else null,
            median, gaps.lastOrNull(), gaps.size, heatmap, triggers, tagged.size, tools, associations,
        )
    }
}

/** Scenario arithmetic only; no plan mutation and no promised health or financial outcome. */
object SavingsProjection {
    fun estimate(fewerPerDay: Int, days: Int, pricePerPack: Double, cigarettesPerPack: Int): BigDecimal {
        if (fewerPerDay <= 0 || days <= 0 || !pricePerPack.isFinite() || pricePerPack <= 0 || cigarettesPerPack <= 0)
            return BigDecimal.ZERO.setScale(2)
        return BigDecimal.valueOf(pricePerPack)
            .multiply(BigDecimal.valueOf(fewerPerDay.toLong() * days.toLong()))
            .divide(BigDecimal(cigarettesPerPack), 2, RoundingMode.HALF_UP)
    }

    fun daysToReward(remaining: Double, fewerPerDay: Int, pricePerPack: Double, cigarettesPerPack: Int): Long? {
        if (!remaining.isFinite()) return null
        if (remaining <= 0) return 0
        if (fewerPerDay <= 0 || pricePerPack <= 0 || !pricePerPack.isFinite() || cigarettesPerPack <= 0) return null
        return BigDecimal.valueOf(remaining).multiply(BigDecimal(cigarettesPerPack))
            .divide(BigDecimal.valueOf(pricePerPack).multiply(BigDecimal(fewerPerDay)), 0, RoundingMode.CEILING)
            .min(BigDecimal(Long.MAX_VALUE)).toLong()
    }
}

fun String.insightLabel(): String = lowercase(Locale.ROOT).replace('_', ' ')
    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
