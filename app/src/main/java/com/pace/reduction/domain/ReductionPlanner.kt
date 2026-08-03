package com.pace.reduction.domain

object ReductionPlanner {
    fun suggestedCeiling(
        currentCeiling: Int,
        step: Int,
        reviewIntervalDays: Int,
        completedDays: List<DailyProgress>,
    ): Int? {
        val evidence = completedDays.filter { it.completed && it.recorded }.takeLast(reviewIntervalDays)
        if (evidence.size < reviewIntervalDays) return null
        val steadyRatio = evidence.count { it.steady == true }.toDouble() / evidence.size
        return if (steadyRatio >= 0.8) (currentCeiling - step.coerceIn(1, 5)).coerceAtLeast(0) else null
    }
}
