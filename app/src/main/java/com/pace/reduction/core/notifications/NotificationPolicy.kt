package com.pace.reduction.core.notifications

object NotificationPolicy {
    private const val THREE_HOURS_MS = 3 * 60 * 60 * 1_000L
    private const val THIRTY_MINUTES_MS = 30 * 60 * 1_000L

    fun isEligible(
        nowEpochMs: Long,
        quietHours: Boolean,
        countToday: Int,
        dailyMaximum: Int,
        lastNotificationEpochMs: Long,
        dismissedUntilEpochMs: Long,
        latestLogEpochMs: Long,
    ): Boolean = !quietHours &&
        countToday < dailyMaximum &&
        nowEpochMs - lastNotificationEpochMs >= THREE_HOURS_MS &&
        nowEpochMs >= dismissedUntilEpochMs &&
        (latestLogEpochMs == 0L || nowEpochMs - latestLogEpochMs >= THIRTY_MINUTES_MS)
}
