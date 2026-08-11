package com.pace.reduction.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaceDao {
    @Query("SELECT * FROM cigarette_logs ORDER BY occurredAtEpochMs DESC")
    fun observeAllLogs(): Flow<List<CigaretteLogEntity>>

    @Query("SELECT * FROM cigarette_logs WHERE reversedAtEpochMs IS NULL ORDER BY occurredAtEpochMs DESC")
    fun observeActiveLogs(): Flow<List<CigaretteLogEntity>>

    @Query("SELECT * FROM cigarette_logs WHERE reversedAtEpochMs IS NULL ORDER BY recordedAtEpochMs DESC LIMIT 1")
    suspend fun latestActiveLog(): CigaretteLogEntity?

    @Query(
        "SELECT * FROM cigarette_logs " +
            "WHERE reversedAtEpochMs IS NULL AND occurredAtEpochMs >= :startEpochMs " +
            "AND occurredAtEpochMs < :endEpochMs ORDER BY occurredAtEpochMs ASC",
    )
    suspend fun activeLogsBetween(startEpochMs: Long, endEpochMs: Long): List<CigaretteLogEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: CigaretteLogEntity)

    @Query(
        "UPDATE cigarette_logs SET reversedAtEpochMs = :reversedAtEpochMs, " +
            "reversalReason = :reason WHERE id = :id AND reversedAtEpochMs IS NULL",
    )
    suspend fun reverseLog(id: String, reversedAtEpochMs: Long, reason: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailySnapshot(snapshot: DailyPlanSnapshotEntity): Long

    @Query("SELECT * FROM daily_plan_snapshots WHERE localDate = :localDate LIMIT 1")
    suspend fun dailySnapshot(localDate: String): DailyPlanSnapshotEntity?

    @Query("SELECT * FROM daily_plan_snapshots ORDER BY localDate ASC")
    fun observeDailySnapshots(): Flow<List<DailyPlanSnapshotEntity>>

    @Query("SELECT * FROM daily_plan_snapshots ORDER BY localDate ASC")
    suspend fun allDailySnapshots(): List<DailyPlanSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUrgeSession(session: UrgeSessionEntity)

    @Query("SELECT * FROM urge_sessions ORDER BY startedAtEpochMs DESC")
    fun observeUrgeSessions(): Flow<List<UrgeSessionEntity>>

    @Query("SELECT * FROM urge_sessions ORDER BY startedAtEpochMs ASC")
    suspend fun allUrgeSessions(): List<UrgeSessionEntity>

    @Query(
        "UPDATE urge_sessions SET endedAtEpochMs = :endedAtEpochMs, urgeAfter = :urgeAfter, " +
            "completed = :completed, smokedAfter = :smokedAfter WHERE id = :id",
    )
    suspend fun finishUrgeSession(
        id: String,
        endedAtEpochMs: Long,
        urgeAfter: Int?,
        completed: Boolean,
        smokedAfter: Boolean?,
    ): Int

    @Query("SELECT * FROM cigarette_logs ORDER BY occurredAtEpochMs ASC")
    suspend fun allLogs(): List<CigaretteLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: AchievementEntity): Long

    @Query("SELECT * FROM achievements ORDER BY unlockedAtEpochMs ASC")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements ORDER BY unlockedAtEpochMs ASC")
    suspend fun allAchievements(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExternalBreak(externalBreak: ExternalBreakEntity)

    @Query("SELECT * FROM external_breaks ORDER BY startedAtEpochMs ASC")
    suspend fun allExternalBreaks(): List<ExternalBreakEntity>

    @Query("SELECT * FROM external_breaks ORDER BY startedAtEpochMs ASC")
    fun observeExternalBreaks(): Flow<List<ExternalBreakEntity>>

    @Query("DELETE FROM cigarette_logs WHERE id = :id")
    suspend fun deleteLog(id: String): Int

    @Query("DELETE FROM cigarette_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM urge_sessions")
    suspend fun deleteAllUrgeSessions()

    @Query("DELETE FROM daily_plan_snapshots")
    suspend fun deleteAllDailySnapshots()

    @Query("DELETE FROM achievements")
    suspend fun deleteAllAchievements()

    @Query("DELETE FROM personal_records")
    suspend fun deleteAllPersonalRecords()

    @Query("DELETE FROM external_breaks")
    suspend fun deleteAllExternalBreaks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoachMessage(message: CoachMessageEntity)

    @Query("SELECT * FROM coach_messages ORDER BY createdAtEpochMs ASC")
    fun observeCoachMessages(): Flow<List<CoachMessageEntity>>

    @Query("SELECT * FROM coach_messages ORDER BY createdAtEpochMs ASC")
    suspend fun allCoachMessages(): List<CoachMessageEntity>

    @Query("DELETE FROM coach_messages")
    suspend fun deleteAllCoachMessages()

    @Query("SELECT * FROM step_days ORDER BY localDate ASC")
    fun observeStepDays(): Flow<List<StepDayEntity>>

    @Query("SELECT * FROM step_days ORDER BY localDate ASC")
    suspend fun allStepDays(): List<StepDayEntity>

    /** Null when the day has no row yet, which is not the same as a day walked with no steps. */
    @Query("SELECT steps FROM step_days WHERE localDate = :localDate")
    suspend fun stepsOn(localDate: String): Long?

    /**
     * Adds [delta] to a day, creating the row if this is the day's first reading.
     *
     * Done as one upsert rather than read-modify-write so two samples racing — the foreground
     * sampler and the periodic worker can land together — cannot lose steps between them.
     */
    @Query(
        "INSERT INTO step_days (localDate, steps, updatedAtEpochMs) " +
            "VALUES (:localDate, :delta, :updatedAtEpochMs) " +
            "ON CONFLICT(localDate) DO UPDATE SET " +
            "steps = steps + :delta, updatedAtEpochMs = :updatedAtEpochMs",
    )
    suspend fun addSteps(localDate: String, delta: Long, updatedAtEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStepDay(day: StepDayEntity)

    @Query("DELETE FROM step_days")
    suspend fun deleteAllStepDays()
}
