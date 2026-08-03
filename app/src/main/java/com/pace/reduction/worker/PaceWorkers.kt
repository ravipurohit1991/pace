package com.pace.reduction.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pace.reduction.PaceApplication
import com.pace.reduction.core.notifications.PaceNotifications
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private fun Context.repository() = (applicationContext as PaceApplication).container.repository

private fun Context.coachService() = (applicationContext as PaceApplication).container.coachService

class DailyRolloverWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        applicationContext.repository().refreshWidgetSnapshot()
    }.fold({ Result.success() }, { Result.retry() })
}

class CoachingEligibilityWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (PaceNotifications.canPost(applicationContext) && applicationContext.repository().claimCoachingNotification()) {
            PaceNotifications.postCoaching(applicationContext)
        }
    }.fold({ Result.success() }, { Result.retry() })
}

class WeeklyReviewWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        applicationContext.repository().evaluateStoredAchievements()
    }.fold({ Result.success() }, { Result.retry() })
}

/**
 * Writes a fresh nudge with the cloud model when the next planned window is close, then posts it.
 * Falls back to the built-in line if the model is unreachable, so the user still gets the cue.
 */
class CoachNudgeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (!PaceNotifications.canPost(applicationContext)) return@runCatching
        if (!applicationContext.repository().claimProactiveNudge()) return@runCatching
        val message = runCatching { applicationContext.coachService().nudge() }.getOrDefault("")
        PaceNotifications.postCoachNudge(applicationContext, message)
    }.fold({ Result.success() }, { Result.retry() })
}

/**
 * Unprompted check-in. Unlike the nudge, this is not tied to an approaching window — it just keeps
 * the coach present through the day with something funny or curious while the app is closed.
 */
class CoachCheckupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (!PaceNotifications.canPost(applicationContext)) return@runCatching
        if (!applicationContext.repository().claimCheckup()) return@runCatching
        val message = runCatching { applicationContext.coachService().checkup() }.getOrDefault("")
        if (message.isNotBlank()) PaceNotifications.postCoachCheckup(applicationContext, message)
    }.fold({ Result.success() }, { Result.retry() })
}

/** Keeps a fresh line on the home screen, replaced about once an hour. */
class WidgetQuoteWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (!applicationContext.repository().widgetQuoteIsStale()) return@runCatching
        val quote = runCatching { applicationContext.coachService().quote() }.getOrDefault("")
        applicationContext.repository().saveWidgetQuote(quote)
    }.fold({ Result.success() }, { Result.retry() })
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        applicationContext.repository().refreshWidgetSnapshot()
    }.fold({ Result.success() }, { Result.retry() })
}

object PaceWorkScheduler {
    private const val TAG = "pace"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val now = ZonedDateTime.now()
        val untilNextDay = Duration.between(
            now,
            now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(5),
        ).toMinutes().coerceAtLeast(15)
        workManager.enqueueUniquePeriodicWork(
            "pace-daily-rollover",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyRolloverWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(untilNextDay, TimeUnit.MINUTES).addTag(TAG).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "pace-coaching-eligibility",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CoachingEligibilityWorker>(3, TimeUnit.HOURS).addTag(TAG).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "pace-weekly-review",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WeeklyReviewWorker>(7, TimeUnit.DAYS).addTag(TAG).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "pace-widget-refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS).addTag(TAG).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "pace-widget-quote",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetQuoteWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .addTag(TAG)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "pace-coach-checkup",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CoachCheckupWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .addTag(TAG)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "pace-coach-nudge",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CoachNudgeWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .addTag(TAG)
                .build(),
        )
    }
}
