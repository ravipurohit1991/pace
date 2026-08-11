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
import com.pace.reduction.R
import com.pace.reduction.core.steps.StepSensor
import com.pace.reduction.core.notifications.PaceNotifications
import com.pace.reduction.domain.CoachBeat
import com.pace.reduction.feature.moveTitleRes
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

/**
 * Samples the pedometer on a schedule as well as on app resume.
 *
 * Without this, a day the user never opened the app would have its steps credited to whenever they
 * next did — the counter is cumulative, so the delta would arrive intact but land on the wrong day.
 * Reading a few times a day keeps each day's total roughly where it belongs.
 */
class StepSampleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val raw = StepSensor(applicationContext).readCounter()
        if (raw != null) applicationContext.repository().recordStepReading(raw)
    }.fold({ Result.success() }, { Result.retry() })
}

class CoachingEligibilityWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (PaceNotifications.canPost(applicationContext) && applicationContext.repository().claimCoachingNotification()) {
            PaceNotifications.postCoaching(
                applicationContext,
                applicationContext.repository().notificationsArePrivate(),
            )
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
        PaceNotifications.postCoachNudge(
            applicationContext,
            message,
            applicationContext.repository().notificationsArePrivate(),
        )
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
        if (message.isNotBlank()) {
            PaceNotifications.postCoachCheckup(
                applicationContext,
                message,
                applicationContext.repository().notificationsArePrivate(),
            )
        }
    }.fold({ Result.success() }, { Result.retry() })
}

/**
 * The coach's own daily agenda: a plan in the morning, invitations to move through the working day,
 * and one line in the evening.
 *
 * Unlike the nudge and the check-in, whatever it writes is also appended to the chat, so opening the
 * notification lands in a conversation that has already started rather than an empty composer.
 */
class CoachAgendaWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (!PaceNotifications.canPost(applicationContext)) return@runCatching
        val repository = applicationContext.repository()
        val beat = repository.claimCoachBeat() ?: return@runCatching
        val session = if (beat == CoachBeat.MOVE_INVITE) repository.suggestedMoveSession() else null
        val moveTitle = session?.let { applicationContext.getString(moveTitleRes(it.id)) }
        val message = runCatching {
            applicationContext.coachService().agendaMessage(beat, moveTitle)
        }.getOrDefault("")
        val text = message.ifBlank { applicationContext.getString(fallbackFor(beat)) }
        // Kept in the chat so the thread reads as one ongoing conversation rather than a series of
        // notifications the coach has no memory of having sent.
        repository.appendCoachMessage("assistant", text)
        PaceNotifications.postCoachBeat(
            context = applicationContext,
            beat = beat,
            message = text,
            moveSessionId = session?.id,
            privateOnLockScreen = repository.notificationsArePrivate(),
        )
    }.fold({ Result.success() }, { Result.retry() })

    private fun fallbackFor(beat: CoachBeat): Int = when (beat) {
        CoachBeat.MORNING_PLAN -> R.string.agenda_morning_fallback
        CoachBeat.MOVE_INVITE -> R.string.agenda_move_fallback
        CoachBeat.EVENING_REFLECT -> R.string.agenda_evening_fallback
    }
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
            "pace-step-sample",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<StepSampleWorker>(4, TimeUnit.HOURS).addTag(TAG).build(),
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
        // Half-hourly: the agenda's own windows are the rate limit, so this only has to wake often
        // enough that a morning message does not arrive at lunchtime.
        workManager.enqueueUniquePeriodicWork(
            "pace-coach-agenda",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CoachAgendaWorker>(30, TimeUnit.MINUTES)
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
