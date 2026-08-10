package com.pace.reduction.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pace.reduction.data.db.PaceDatabase
import com.pace.reduction.data.db.StepDayEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepDayDaoTest {
    private lateinit var database: PaceDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaceDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repeatedReadingsAccumulateOntoTheSameDay() = runBlocking {
        val dao = database.paceDao()

        // Three samples through one day, as the foreground sampler and the worker would deliver.
        dao.addSteps("2026-08-10", 1_200, 1)
        dao.addSteps("2026-08-10", 800, 2)
        dao.addSteps("2026-08-10", 45, 3)

        val day = dao.allStepDays().single()
        assertEquals("2026-08-10", day.localDate)
        assertEquals(2_045L, day.steps)
        assertEquals(3L, day.updatedAtEpochMs)
    }

    @Test
    fun separateDaysAreKeptApartAndOrderedAscending() = runBlocking {
        val dao = database.paceDao()
        dao.addSteps("2026-08-11", 500, 10)
        dao.addSteps("2026-08-09", 300, 5)
        dao.addSteps("2026-08-10", 700, 7)
        dao.addSteps("2026-08-10", 100, 8)

        val days = dao.observeStepDays().first()
        assertEquals(listOf("2026-08-09", "2026-08-10", "2026-08-11"), days.map { it.localDate })
        assertEquals(listOf(300L, 800L, 500L), days.map { it.steps })
    }

    @Test
    fun importReplacesADayRatherThanAddingToIt() = runBlocking {
        // Restoring a backup must land on the recorded figure, not on that plus whatever the
        // device had already counted today.
        val dao = database.paceDao()
        dao.addSteps("2026-08-10", 4_000, 1)
        dao.upsertStepDay(StepDayEntity("2026-08-10", 9_000, 2))

        assertEquals(9_000L, dao.allStepDays().single().steps)
    }

    @Test
    fun deletingClearsEveryDay() = runBlocking {
        val dao = database.paceDao()
        dao.addSteps("2026-08-10", 4_000, 1)
        dao.deleteAllStepDays()
        assertTrue(dao.allStepDays().isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class StepMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PaceDatabase::class.java,
    )

    /**
     * A user upgrading into step counting must keep the history they already had — the new table
     * is additive, and nothing else may be touched on the way through.
     */
    @Test
    fun migratingToFourAddsStepsWithoutDisturbingExistingRows() {
        helper.createDatabase(TEST_DB, 3).use { old ->
            old.execSQL(
                "INSERT INTO cigarette_logs " +
                    "(id, occurredAtEpochMs, recordedAtEpochMs, source, note, reversedAtEpochMs, reversalReason) " +
                    "VALUES ('kept', 1000, 1000, 'APP', NULL, NULL, NULL)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            PaceDatabase.MIGRATION_3_4,
        )

        migrated.query("SELECT id FROM cigarette_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("kept", cursor.getString(0))
        }
        migrated.query("SELECT count(*) FROM step_days").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
