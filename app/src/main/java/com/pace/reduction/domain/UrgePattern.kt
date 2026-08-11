package com.pace.reduction.domain

import com.pace.reduction.domain.model.CigaretteLog
import java.time.LocalDate
import java.time.ZoneId

/** A stretch of the day the user's own history keeps landing in. */
data class UrgeWindow(
    val startMinutes: Int,
    val endMinutes: Int,
    /** Fraction of the sampled moments that fall inside the window, 0..1. */
    val share: Float,
    /** How much denser the window is than an evenly spread day. 2.0 means twice as busy. */
    val lift: Float,
)

/**
 * What the log timestamps say about when the day is hardest.
 *
 * [window] is null until there is enough history to mean anything, and stays null when the moments
 * are spread evenly — proposing a "hardest stretch" to somebody whose day is flat would be inventing
 * a pattern rather than finding one.
 */
data class UrgePattern(
    val window: UrgeWindow? = null,
    val sampleSize: Int = 0,
    val daysCovered: Int = 0,
    /** Hours of the day with the most moments, busiest first. At most three. */
    val busiestHours: List<Int> = emptyList(),
)

object UrgePatterns {
    /** How far back to look. A month is long enough to average out one strange week. */
    const val WINDOW_DAYS = 30

    /** Below this there is no pattern, only a handful of points. */
    private const val MIN_SAMPLE = 20
    private const val MIN_DAYS = 5

    /** Half-hour resolution: finer than the user could act on, coarser than the noise. */
    private const val SLOT_MINUTES = 30
    private const val SLOTS = 24 * 60 / SLOT_MINUTES

    /** Candidate widths, in slots: two, two and a half, three hours. */
    private val BAND_SLOTS = listOf(4, 5, 6)

    /** A window has to be at least this much denser than a flat day to be worth suggesting. */
    private const val MIN_LIFT = 1.5f

    /** …and hold at least this share of the moments, so a sharp spike on a quiet day is ignored. */
    private const val MIN_SHARE = 0.22f

    fun analyse(
        today: LocalDate,
        zoneId: ZoneId,
        logs: List<CigaretteLog>,
        windowDays: Int = WINDOW_DAYS,
    ): UrgePattern {
        val from = today.minusDays(windowDays - 1L)
        val moments = logs
            .asSequence()
            .filter { it.reversedAt == null }
            .map { it.occurredAt.atZone(zoneId) }
            .filter { !it.toLocalDate().isBefore(from) && !it.toLocalDate().isAfter(today) }
            .toList()

        val daysCovered = moments.map { it.toLocalDate() }.distinct().size
        val busiestHours = moments
            .groupingBy { it.hour }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(3)
            .map { it.key }

        if (moments.size < MIN_SAMPLE || daysCovered < MIN_DAYS) {
            return UrgePattern(
                sampleSize = moments.size,
                daysCovered = daysCovered,
                busiestHours = busiestHours,
            )
        }

        val slots = IntArray(SLOTS)
        moments.forEach { slots[(it.hour * 60 + it.minute) / SLOT_MINUTES]++ }

        val best = BAND_SLOTS
            .flatMap { length -> (0 until SLOTS).map { start -> bandAt(slots, start, length, moments.size) } }
            .filter { it.share >= MIN_SHARE && it.lift >= MIN_LIFT }
            .maxWithOrNull(compareBy<UrgeWindow> { it.lift }.thenBy { it.share })

        return UrgePattern(
            window = best,
            sampleSize = moments.size,
            daysCovered = daysCovered,
            busiestHours = busiestHours,
        )
    }

    /**
     * Sums a band of slots, wrapping past midnight so a late-evening window is not cut in half.
     *
     * The band is then trimmed back to the slots that actually hold something. Without that, several
     * different starting positions cover exactly the same moments and score identically, and which
     * one wins comes down to the order they were generated in — so a cluster that begins at ten past
     * three could be reported as beginning at half past two. Trimming makes the answer the data's
     * rather than the loop's, and a tighter band is honestly denser, so it also wins on its own
     * merits.
     */
    private fun bandAt(slots: IntArray, start: Int, length: Int, total: Int): UrgeWindow {
        var first = -1
        var last = -1
        var inside = 0
        for (offset in 0 until length) {
            val count = slots[(start + offset) % SLOTS]
            if (count > 0) {
                if (first < 0) first = offset
                last = offset
            }
            inside += count
        }
        if (first < 0) return UrgeWindow(start * SLOT_MINUTES, start * SLOT_MINUTES, 0f, 0f)
        val trimmed = last - first + 1
        val share = inside.toFloat() / total
        return UrgeWindow(
            startMinutes = ((start + first) % SLOTS) * SLOT_MINUTES,
            endMinutes = ((start + last + 1) % SLOTS) * SLOT_MINUTES,
            share = share,
            lift = share / (trimmed.toFloat() / SLOTS),
        )
    }

    /** True when [minuteOfDay] falls inside a window, wrapping past midnight. */
    fun contains(startMinutes: Int, endMinutes: Int, minuteOfDay: Int): Boolean =
        if (startMinutes <= endMinutes) {
            minuteOfDay in startMinutes until endMinutes
        } else {
            minuteOfDay >= startMinutes || minuteOfDay < endMinutes
        }
}
