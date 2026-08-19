package com.pace.reduction.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CigaretteLogEntity::class,
        BeverageLogEntity::class,
        UrgeSessionEntity::class,
        DailyPlanSnapshotEntity::class,
        AchievementEntity::class,
        PersonalRecordEntity::class,
        ExternalBreakEntity::class,
        CoachMessageEntity::class,
        StepDayEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class PaceDatabase : RoomDatabase() {
    abstract fun paceDao(): PaceDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `beverage_logs` (" +
                        "`id` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`occurredAtEpochMs` INTEGER NOT NULL, " +
                        "`recordedAtEpochMs` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`reversedAtEpochMs` INTEGER, " +
                        "`reversalReason` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `step_days` (" +
                        "`localDate` TEXT NOT NULL, " +
                        "`steps` INTEGER NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`localDate`))",
                )
            }
        }

        /** Location trigger places were removed; the table goes with them. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `trigger_places`")
            }
        }

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
