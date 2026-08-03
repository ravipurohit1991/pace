package com.pace.reduction.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CigaretteLogEntity::class,
        UrgeSessionEntity::class,
        DailyPlanSnapshotEntity::class,
        AchievementEntity::class,
        PersonalRecordEntity::class,
        TriggerPlaceEntity::class,
        ExternalBreakEntity::class,
        CoachMessageEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PaceDatabase : RoomDatabase() {
    abstract fun paceDao(): PaceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `coach_messages` (" +
                        "`id` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
