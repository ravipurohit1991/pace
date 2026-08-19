package com.pace.reduction.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pace.reduction.data.db.BeverageLogEntity
import com.pace.reduction.data.db.PaceDatabase
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
class BeverageLogDaoTest {
    private lateinit var database: PaceDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaceDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun reversalKeepsTheAuditRowButDropsTheActiveCount() = runBlocking {
        val dao = database.paceDao()
        dao.insertBeverageLog(BeverageLogEntity("one", "COFFEE", 100, 100, "APP", null, null))

        assertEquals(1, dao.observeActiveBeverageLogs().first().size)
        assertEquals(1, dao.reverseBeverageLog("one", 200, "USER_UNDO"))
        assertTrue(dao.observeActiveBeverageLogs().first().isEmpty())
        assertEquals(200L, dao.allBeverageLogs().single().reversedAtEpochMs)
    }
}

@RunWith(AndroidJUnit4::class)
class BeverageMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PaceDatabase::class.java,
    )

    @Test
    fun migratingToFiveAddsBeveragesWithoutTouchingCigarettes() {
        helper.createDatabase(TEST_DB, 4).use { old ->
            old.execSQL(
                "INSERT INTO cigarette_logs " +
                    "(id, occurredAtEpochMs, recordedAtEpochMs, source, note, reversedAtEpochMs, reversalReason) " +
                    "VALUES ('kept', 1000, 1000, 'APP', NULL, NULL, NULL)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            PaceDatabase.MIGRATION_4_5,
        )

        migrated.query("SELECT id FROM cigarette_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("kept", cursor.getString(0))
        }
        migrated.query("SELECT count(*) FROM beverage_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "beverage-migration-test.db"
    }
}
