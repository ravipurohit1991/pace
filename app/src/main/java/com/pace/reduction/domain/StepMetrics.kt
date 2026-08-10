package com.pace.reduction.domain

import java.time.LocalDate

/** One local day of walking, with the distance already resolved from the user's stride. */
data class StepDay(
    val date: LocalDate,
    val steps: Long,
    val distanceKm: Double,
)

/**
 * Everything the Progress screen says about walking.
 *
 * [available] and [permissionGranted] are carried here rather than checked in the UI so a device
 * with no pedometer and a device whose permission was declined can say different things.
 */
data class StepMetrics(
    val days: List<StepDay> = emptyList(),
    val todaySteps: Long = 0,
    val todayDistanceKm: Double = 0.0,
    val sevenDayAverage: Double = 0.0,
    val thirtyDayAverage: Double = 0.0,
    val totalSteps: Long = 0,
    val totalDistanceKm: Double = 0.0,
    val bestDay: StepDay? = null,
    val available: Boolean = false,
    val permissionGranted: Boolean = false,
    val enabled: Boolean = false,
) {
    /** True once there is at least one day with steps on it — the screen stays quiet until then. */
    val hasData: Boolean get() = days.any { it.steps > 0 }
}

object StepCalculator {

    /** Fallback when no height is set: a middling adult walking stride. */
    const val DEFAULT_HEIGHT_CM = 170

    /**
     * Stride as a fraction of height.
     *
     * The 0.414 factor is the usual pedometer estimate for a normal walking pace. It is an estimate
     * and the app says so rather than presenting the distance as measured — deriving it from height
     * is still far closer than assuming one stride length for everybody.
     */
    private const val STRIDE_FACTOR = 0.414

    fun strideMetres(heightCentimetres: Int): Double {
        val height = if (heightCentimetres in 100..250) heightCentimetres else DEFAULT_HEIGHT_CM
        return height * STRIDE_FACTOR / 100.0
    }

    fun distanceKm(steps: Long, heightCentimetres: Int): Double =
        steps * strideMetres(heightCentimetres) / 1_000.0

    /**
     * Turns a raw cumulative sensor reading into the number of steps to add.
     *
     * The platform's `TYPE_STEP_COUNTER` counts from boot and resets to zero when the device
     * restarts, so a reading lower than the last one means a reboot happened rather than that the
     * user walked backwards — and in that case the whole new reading is steps taken since the
     * restart. Steps taken between the last sample and the reboot are genuinely unrecoverable;
     * nothing short of a foreground service would have caught them.
     */
    fun deltaFor(previousAnchor: Long?, raw: Long): Long = when {
        raw < 0 -> 0
        previousAnchor == null -> 0
        raw < previousAnchor -> raw
        else -> raw - previousAnchor
    }

    /**
     * @param days every recorded day, ascending. Days with no row are treated as unrecorded rather
     *   than as zero, so a phone left at home does not drag the averages down.
     */
    fun calculate(
        today: LocalDate,
        days: List<StepDay>,
        available: Boolean,
        permissionGranted: Boolean,
        enabled: Boolean,
    ): StepMetrics {
        val ordered = days.sortedBy { it.date }
        val todayEntry = ordered.lastOrNull { it.date == today }
        return StepMetrics(
            days = ordered,
            todaySteps = todayEntry?.steps ?: 0,
            todayDistanceKm = todayEntry?.distanceKm ?: 0.0,
            sevenDayAverage = averageOver(ordered, today, 7),
            thirtyDayAverage = averageOver(ordered, today, 30),
            totalSteps = ordered.sumOf { it.steps },
            totalDistanceKm = ordered.sumOf { it.distanceKm },
            bestDay = ordered.filter { it.steps > 0 }.maxByOrNull { it.steps },
            available = available,
            permissionGranted = permissionGranted,
            enabled = enabled,
        )
    }

    /**
     * Mean over the days actually recorded in the window.
     *
     * Dividing by the window length instead would report a lower daily average every time the user
     * left their phone on a table, which reads as a decline that did not happen.
     */
    private fun averageOver(days: List<StepDay>, today: LocalDate, window: Int): Double {
        val from = today.minusDays(window - 1L)
        val inWindow = days.filter { !it.date.isBefore(from) && !it.date.isAfter(today) && it.steps > 0 }
        if (inWindow.isEmpty()) return 0.0
        return inWindow.sumOf { it.steps }.toDouble() / inWindow.size
    }
}
