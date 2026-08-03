package com.pace.reduction.data.seed

import com.pace.reduction.BuildConfig
import com.pace.reduction.data.repository.PaceRepository
import java.time.LocalDate
import java.time.LocalTime

/**
 * Carries an owner's existing history into a fresh install of this build.
 *
 * Values come from `local.properties` (git-ignored) via BuildConfig, so a clean checkout seeds
 * nothing and behaves like a normal first run. Seeding happens once and is recorded in
 * preferences, so reinstalling over existing data never duplicates logs.
 */
object ProvisioningSeeder {
    suspend fun applyIfNeeded(repository: PaceRepository) {
        if (!hasAnythingToSeed()) return
        repository.applyProvisioning(
            apiKey = BuildConfig.SEED_OLLAMA_KEY,
            model = BuildConfig.SEED_OLLAMA_MODEL,
            yesterdayCount = BuildConfig.SEED_YESTERDAY_COUNT,
            todayCount = BuildConfig.SEED_TODAY_COUNT,
            lastTimeToday = parseTime(BuildConfig.SEED_LAST_TIME),
            ceiling = BuildConfig.SEED_CEILING,
            spacingMinutes = BuildConfig.SEED_SPACING,
        )
    }

    private fun hasAnythingToSeed(): Boolean =
        BuildConfig.SEED_OLLAMA_KEY.isNotBlank() ||
            BuildConfig.SEED_YESTERDAY_COUNT > 0 ||
            BuildConfig.SEED_TODAY_COUNT > 0 ||
            BuildConfig.SEED_CEILING > 0

    private fun parseTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value) }.getOrNull()

    /**
     * Spreads [count] cigarettes evenly across a plausible waking window, ending at [last] when one
     * is known. Even spacing keeps the derived gap and hold statistics sane.
     */
    fun timesFor(count: Int, date: LocalDate, last: LocalTime?, dayStart: LocalTime): List<LocalTime> {
        if (count <= 0) return emptyList()
        val end = last ?: LocalTime.of(22, 0)
        if (count == 1) return listOf(end)
        val startMinute = dayStart.toSecondOfDay() / 60
        val endMinute = end.toSecondOfDay() / 60
        if (endMinute <= startMinute) return List(count) { end }
        val stepMinutes = (endMinute - startMinute).toDouble() / (count - 1)
        return (0 until count).map { index ->
            LocalTime.ofSecondOfDay(((startMinute + stepMinutes * index).toLong() * 60).coerceIn(0, 86_399))
        }
    }
}
