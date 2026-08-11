package com.pace.reduction.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class PaceBackup(
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val plan: PlanBackup,
    val logs: List<LogBackup>,
    val urgeSessions: List<UrgeBackup>,
    val dailySnapshots: List<SnapshotBackup>,
    val achievements: List<AchievementBackup>,
    val externalBreaks: List<ExternalBreakBackup>,
    /** Defaulted so backups written before step counting existed still import. */
    val stepDays: List<StepDayBackup> = emptyList(),
)

@Serializable
data class StepDayBackup(
    val localDate: String,
    val steps: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class PlanBackup(
    val baselinePerDay: Int,
    val dailyCeiling: Int,
    val minimumGapMinutes: Int,
    val wakeMinutes: Int,
    val sleepMinutes: Int,
    val weekendWakeEnabled: Boolean,
    val weekendWakeMinutes: Int,
    val morningHoldMinutes: Int,
    val flexibleDay: Boolean,
    val reductionStep: Int,
    val reviewIntervalDays: Int,
    val pricePerPack: Double,
    val cigarettesPerPack: Int,
    val currencyCode: String,
    val personalReason: String,
    val rewardName: String,
    val rewardTarget: Double,
    val coachingTone: String,
    val reminderIntensity: String,
    val notificationPrivate: Boolean,
    val hapticsEnabled: Boolean,
    val themeMode: String,
    /** Defaulted so backups written before values existed still import. */
    val personalValues: List<String> = emptyList(),
)

@Serializable
data class LogBackup(
    val id: String,
    val occurredAtEpochMs: Long,
    val recordedAtEpochMs: Long,
    val source: String,
    val note: String?,
    val reversedAtEpochMs: Long?,
    val reversalReason: String?,
)

@Serializable
data class UrgeBackup(
    val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val tool: String,
    val urgeBefore: Int?,
    val urgeAfter: Int?,
    val triggerTagsJson: String,
    val note: String?,
    val completed: Boolean,
    val smokedAfter: Boolean?,
    val externalRef: String?,
)

@Serializable
data class SnapshotBackup(
    val localDate: String,
    val zoneId: String,
    val baseline: Int,
    val ceiling: Int,
    val minimumGapMinutes: Int,
    val wakeMinutes: Int,
    val sleepMinutes: Int,
    val morningHoldMinutes: Int,
    val flexibleDay: Boolean,
    val createdAtEpochMs: Long,
)

@Serializable
data class AchievementBackup(val badgeId: String, val unlockedAtEpochMs: Long, val evidenceJson: String)

@Serializable
data class ExternalBreakBackup(
    val id: String,
    val gameId: String,
    val startedAtEpochMs: Long,
    val returnedAtEpochMs: Long?,
    val urgeSessionId: String?,
)
