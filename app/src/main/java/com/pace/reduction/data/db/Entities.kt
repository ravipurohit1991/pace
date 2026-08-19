package com.pace.reduction.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cigarette_logs")
data class CigaretteLogEntity(
    @PrimaryKey val id: String,
    val occurredAtEpochMs: Long,
    val recordedAtEpochMs: Long,
    val source: String,
    val note: String?,
    val reversedAtEpochMs: Long?,
    val reversalReason: String?,
)

@Entity(tableName = "beverage_logs")
data class BeverageLogEntity(
    @PrimaryKey val id: String,
    val type: String,
    val occurredAtEpochMs: Long,
    val recordedAtEpochMs: Long,
    val source: String,
    val reversedAtEpochMs: Long?,
    val reversalReason: String?,
)

@Entity(tableName = "urge_sessions")
data class UrgeSessionEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "daily_plan_snapshots")
data class DailyPlanSnapshotEntity(
    @PrimaryKey val localDate: String,
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

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val badgeId: String,
    val unlockedAtEpochMs: Long,
    val evidenceJson: String,
)

@Entity(tableName = "personal_records")
data class PersonalRecordEntity(
    @PrimaryKey val recordType: String,
    val value: Long,
    val achievedAtEpochMs: Long,
    val sourceEventId: String?,
)

@Entity(tableName = "external_breaks")
data class ExternalBreakEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val startedAtEpochMs: Long,
    val returnedAtEpochMs: Long?,
    val urgeSessionId: String?,
)

/**
 * One row per local day of walking.
 *
 * Stored as a running total rather than as individual readings: the platform's step counter is
 * cumulative since boot and is sampled opportunistically, so what the app actually learns each time
 * is "this many more steps since I last looked". Accumulating that into the day is the whole record.
 */
@Entity(tableName = "step_days")
data class StepDayEntity(
    @PrimaryKey val localDate: String,
    val steps: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "coach_messages")
data class CoachMessageEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMs: Long,
    /** "user" or "assistant". */
    val role: String,
    val content: String,
)

