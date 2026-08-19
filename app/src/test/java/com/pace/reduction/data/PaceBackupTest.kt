package com.pace.reduction.data

import com.pace.reduction.data.backup.LogBackup
import com.pace.reduction.data.backup.BeverageLogBackup
import com.pace.reduction.data.backup.PaceBackup
import com.pace.reduction.data.backup.PaceBackupValidator
import com.pace.reduction.data.backup.PlanBackup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaceBackupTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun jsonRoundTripRetainsAuditFields() {
        val backup = validBackup().copy(
            logs = listOf(LogBackup("one", 100, 101, "APP", "note", 200, "USER_UNDO")),
            beverageLogs = listOf(
                BeverageLogBackup("coffee", "COFFEE", 110, 111, "APP", null, null),
            ),
        )
        val decoded = json.decodeFromString(PaceBackup.serializer(), json.encodeToString(PaceBackup.serializer(), backup))

        assertEquals(backup, decoded)
        PaceBackupValidator.validate(decoded, 1_000)
    }

    @Test
    fun malformedDuplicateIdsAreRejectedBeforeMutationBoundary() {
        val duplicate = LogBackup("same", 100, 100, "APP", null, null, null)
        val backup = validBackup().copy(logs = listOf(duplicate, duplicate))

        assertThrows(IllegalArgumentException::class.java) {
            PaceBackupValidator.validate(backup, 1_000)
        }
    }

    @Test
    fun invalidPlanRangeIsRejected() {
        val backup = validBackup().copy(plan = validBackup().plan.copy(dailyCeiling = 101))

        assertThrows(IllegalArgumentException::class.java) {
            PaceBackupValidator.validate(backup, 1_000)
        }
    }

    private fun validBackup() = PaceBackup(
        exportedAtEpochMs = 500,
        plan = PlanBackup(
            baselinePerDay = 12,
            dailyCeiling = 10,
            minimumGapMinutes = 90,
            wakeMinutes = 420,
            sleepMinutes = 1350,
            weekendWakeEnabled = false,
            weekendWakeMinutes = 480,
            morningHoldMinutes = 30,
            flexibleDay = false,
            reductionStep = 1,
            reviewIntervalDays = 7,
            pricePerPack = 60.0,
            cigarettesPerPack = 20,
            currencyCode = "DKK",
            personalReason = "",
            rewardName = "",
            rewardTarget = 0.0,
            coachingTone = "SUPPORTIVE",
            reminderIntensity = "OFF",
            notificationPrivate = true,
            hapticsEnabled = true,
            themeMode = "SYSTEM",
        ),
        logs = emptyList(),
        urgeSessions = emptyList(),
        dailySnapshots = emptyList(),
        achievements = emptyList(),
        externalBreaks = emptyList(),
    )
}
