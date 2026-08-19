package com.pace.reduction.data.backup

import java.time.LocalDate

object PaceBackupValidator {
    fun validate(backup: PaceBackup, nowEpochMs: Long) {
        require(backup.schemaVersion == 1) { "Unsupported backup schema" }
        val plan = backup.plan
        require(plan.baselinePerDay in 1..100 && plan.dailyCeiling in 0..100)
        require(plan.minimumGapMinutes in 15..360 && plan.morningHoldMinutes in 0..240)
        require(plan.wakeMinutes in 0..1439 && plan.sleepMinutes in 0..1439 && plan.wakeMinutes != plan.sleepMinutes)
        require(plan.weekendWakeMinutes in 0..1439 && plan.reductionStep in 1..5 && plan.reviewIntervalDays in 7..28)
        require(plan.cigarettesPerPack in 1..100 && plan.pricePerPack >= 0 && plan.rewardTarget >= 0)
        require(plan.currencyCode.length == 3 && plan.personalReason.length <= 500 && plan.rewardName.length <= 100)
        require(plan.otherBeverageLabel.length <= 24)
        require(plan.coachingTone in setOf("SUPPORTIVE", "DIRECT", "TOUGH"))
        require(plan.reminderIntensity in setOf("OFF", "GENTLE", "STANDARD"))
        require(plan.themeMode in setOf("SYSTEM", "LIGHT", "DARK"))
        requireUnique(backup.logs.map { it.id })
        requireUnique(backup.beverageLogs.map { it.id })
        requireUnique(backup.urgeSessions.map { it.id })
        requireUnique(backup.dailySnapshots.map { it.localDate })
        val latestAllowed = nowEpochMs + 24 * 60 * 60 * 1_000L
        backup.logs.forEach {
            require(it.id.isNotBlank() && it.occurredAtEpochMs in 0..latestAllowed && it.recordedAtEpochMs in 0..latestAllowed)
            require(it.note?.length ?: 0 <= 500 && it.source in setOf("APP", "WIDGET", "MANUAL", "IMPORT"))
        }
        backup.beverageLogs.forEach {
            require(it.id.isNotBlank() && it.occurredAtEpochMs in 0..latestAllowed && it.recordedAtEpochMs in 0..latestAllowed)
            require(it.type in setOf("COFFEE", "ALCOHOL", "OTHER"))
            require(it.source in setOf("APP", "MANUAL", "IMPORT"))
        }
        backup.urgeSessions.forEach {
            require(it.id.isNotBlank() && it.startedAtEpochMs in 0..latestAllowed)
            require(it.urgeBefore == null || it.urgeBefore in 1..5)
            require(it.urgeAfter == null || it.urgeAfter in 1..5)
            require(it.note?.length ?: 0 <= 500 && it.triggerTagsJson.length <= 1_000)
        }
        backup.dailySnapshots.forEach {
            require(runCatching { LocalDate.parse(it.localDate) }.isSuccess)
            require(it.baseline in 1..100 && it.ceiling in 0..100 && it.minimumGapMinutes in 15..360)
        }
    }

    private fun requireUnique(ids: List<String>) {
        require(ids.size == ids.distinct().size && ids.none(String::isBlank)) { "Duplicate or blank IDs" }
    }
}
