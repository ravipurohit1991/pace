package com.pace.reduction.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pace.reduction.PaceApplication
import java.util.concurrent.TimeUnit

/**
 * Keeps the widget's meter and stats moving while a countdown is on screen.
 *
 * The countdown text itself is a system [android.widget.Chronometer] and needs nothing from us —
 * this exists for everything around it that Kotlin has to compute: how full the wait bar is, how
 * long the user has been smoke-free, how much that is worth.
 *
 * Each run re-arms the next one from inside [com.pace.reduction.data.repository.PaceRepository
 * .refreshWidgetSnapshot], so the chain stops on its own the moment the state stops having a
 * deadline. A periodic work request cannot do this: its floor is fifteen minutes, and it would
 * keep firing all night for a widget showing nothing that changes.
 */
class WidgetTickWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        (applicationContext as PaceApplication).container.repository.refreshWidgetSnapshot()
    }.fold(
        onSuccess = { Result.success() },
        // Failing quietly is right here: a missed cosmetic refresh is invisible, and a retry
        // storm on a widget the user may not even have installed is not.
        onFailure = { Result.success() },
    )

    companion object {
        private const val UNIQUE_WORK_NAME = "pace-widget-tick"

        /**
         * Queues the next cosmetic refresh, or cancels the chain.
         *
         * Nothing is queued when ticking is off, when there is no deadline to count toward, or
         * when the deadline arrives before the next tick would — [WidgetBoundaryWorker] already
         * covers that last case exactly.
         */
        fun reschedule(
            context: Context,
            intervalMs: Long?,
            boundaryEpochMs: Long,
            nowEpochMs: Long,
        ) {
            val workManager = WorkManager.getInstance(context)
            val remaining = boundaryEpochMs - nowEpochMs
            if (intervalMs == null || boundaryEpochMs <= 0L || remaining <= intervalMs) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val request = OneTimeWorkRequestBuilder<WidgetTickWorker>()
                .setInitialDelay(intervalMs, TimeUnit.MILLISECONDS)
                .addTag("pace")
                .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
