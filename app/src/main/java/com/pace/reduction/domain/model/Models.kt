package com.pace.reduction.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

enum class CoachingTone { SUPPORTIVE, DIRECT, TOUGH }

enum class ReminderIntensity { OFF, GENTLE, STANDARD }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PlanSettings(
    val onboardingCompleted: Boolean = false,
    val baselinePerDay: Int = 12,
    val dailyCeiling: Int = 12,
    val minimumGapMinutes: Int = 90,
    val wakeMinutes: Int = 7 * 60,
    val sleepMinutes: Int = 22 * 60 + 30,
    val weekendWakeEnabled: Boolean = false,
    val weekendWakeMinutes: Int = 8 * 60,
    val morningHoldMinutes: Int = 30,
    val flexibleDay: Boolean = false,
    val reductionStep: Int = 1,
    val reviewIntervalDays: Int = 7,
    val pricePerPack: Double = 0.0,
    val cigarettesPerPack: Int = 20,
    val currencyCode: String = "DKK",
    val personalReason: String = "",
    val rewardName: String = "",
    val rewardTarget: Double = 0.0,
    val coachingTone: CoachingTone = CoachingTone.SUPPORTIVE,
    val reminderIntensity: ReminderIntensity = ReminderIntensity.OFF,
    val notificationPrivate: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val weatherEnabled: Boolean = false,
    val triggerPlacesEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val quitMode: Boolean = false,
    val quitDate: LocalDate? = null,
    /** When on, the effective minimum gap grows as steady days accumulate. */
    val adaptiveSpacingEnabled: Boolean = false,
    val adaptiveSpacingStepMinutes: Int = 15,
    val adaptiveSpacingIntervalDays: Int = 7,
    val adaptiveSpacingMaxMinutes: Int = 240,
)

/** Ollama Cloud coach configuration. The key is held encrypted at rest. */
data class AiSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val model: String = "",
    val proactiveNudges: Boolean = true,
    /** Empty means "use the built-in persona". */
    val systemPrompt: String = "",
    /** Whether the grounded local figures are attached to each request. */
    val includeStats: Boolean = true,
    val checkupsEnabled: Boolean = false,
    val checkupIntervalMinutes: Int = 180,
) {
    val isReady: Boolean get() = enabled && apiKey.isNotBlank() && model.isNotBlank()
}

data class CoachMessage(
    val id: String,
    val createdAt: Instant,
    val role: String,
    val content: String,
) {
    val isUser: Boolean get() = role == "user"
}

data class ActivePause(
    val sessionId: String = "",
    val startedAt: Instant? = null,
    val endAt: Instant? = null,
    val pausedRemainingMillis: Long = 0L,
) {
    val isRunning: Boolean get() = sessionId.isNotBlank() && endAt != null
    val isPaused: Boolean get() = sessionId.isNotBlank() && endAt == null && pausedRemainingMillis > 0
    val isActive: Boolean get() = sessionId.isNotBlank()
}

data class UrgeSession(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val tool: String,
    val urgeBefore: Int?,
    val urgeAfter: Int?,
    val triggerTags: Set<String>,
    val note: String?,
    val completed: Boolean,
    val smokedAfter: Boolean?,
    val externalRef: String?,
)

data class DailyPlanSnapshot(
    val localDate: LocalDate,
    val baseline: Int,
    val ceiling: Int,
    val minimumGapMinutes: Int,
    val wakeMinutes: Int,
    val morningHoldMinutes: Int,
)

data class Achievement(
    val badgeId: String,
    val unlockedAt: Instant,
    val evidence: String,
)

data class TriggerPlace(
    val id: String,
    val label: String,
    val latitudeRounded: Double,
    val longitudeRounded: Double,
    val radiusMeters: Int,
    val enabled: Boolean,
    val automaticCueEnabled: Boolean,
)

data class CigaretteLog(
    val id: String,
    val occurredAt: Instant,
    val recordedAt: Instant,
    val source: String,
    val reversedAt: Instant?,
)

sealed interface PacingStatus {
    data class Spacing(val earliestWindow: ZonedDateTime) : PacingStatus
    data class WindowMet(val since: ZonedDateTime) : PacingStatus
    data class MorningHold(val until: ZonedDateTime) : PacingStatus
    data class Rest(val nextWake: ZonedDateTime) : PacingStatus
    data object CeilingReached : PacingStatus
    data object Recovery : PacingStatus
}

data class TodaySummary(
    val localDate: LocalDate,
    val count: Int,
    val ceiling: Int,
    val lastLogAt: Instant?,
    val status: PacingStatus,
)

data class QuietSchedule(
    val wake: LocalTime,
    val sleep: LocalTime,
)

data class DailyCount(val date: LocalDate, val count: Int)
