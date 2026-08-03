package com.pace.reduction.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pace.reduction.data.db.AchievementEntity
import com.pace.reduction.data.db.CigaretteLogEntity
import com.pace.reduction.data.db.DailyPlanSnapshotEntity
import com.pace.reduction.data.db.PaceDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaceDaoTest {
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
    fun reversalPreservesAuditRowButRemovesItFromActiveCount() = runBlocking {
        val dao = database.paceDao()
        dao.insertLog(CigaretteLogEntity("one", 100, 100, "APP", null, null, null))

        assertEquals(1, dao.observeActiveLogs().first().size)
        assertEquals(1, dao.reverseLog("one", 200, "USER_UNDO"))
        assertTrue(dao.observeActiveLogs().first().isEmpty())
        val audit = dao.observeAllLogs().first().single()
        assertEquals(200L, audit.reversedAtEpochMs)
        assertEquals("USER_UNDO", audit.reversalReason)
    }

    @Test
    fun daySnapshotAndBadgeAreIdempotent() = runBlocking {
        val dao = database.paceDao()
        val snapshot = DailyPlanSnapshotEntity("2026-08-02", "Europe/Copenhagen", 12, 10, 90, 420, 1350, 30, false, 1)
        assertTrue(dao.insertDailySnapshot(snapshot) > 0)
        assertEquals(-1L, dao.insertDailySnapshot(snapshot))
        val badge = AchievementEntity("first_pause", 100, "evidence")
        assertTrue(dao.insertAchievement(badge) > 0)
        assertEquals(-1L, dao.insertAchievement(badge))
        assertEquals(1, dao.observeAchievements().first().size)
    }
}
