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
 * Re-renders the widget the moment the current window ends, so its countdown flips to "Window met"
 * on time instead of waiting for the next periodic refresh.
 */
class WidgetBoundaryWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        (applicationContext as PaceApplication).container.repository.refreshWidgetSnapshot()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        private const val UNIQUE_WORK_NAME = "pace-widget-boundary"

        /** [boundaryEpochMs] of 0 (or already past) cancels any pending refresh. */
        fun scheduleAt(context: Context, boundaryEpochMs: Long) {
            val workManager = WorkManager.getInstance(context)
            val delayMs = boundaryEpochMs - System.currentTimeMillis()
            if (boundaryEpochMs <= 0L || delayMs <= 0L) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val request = OneTimeWorkRequestBuilder<WidgetBoundaryWorker>()
                // A second past the boundary, so the recomputed state is unambiguous.
                .setInitialDelay(delayMs + 1_000L, TimeUnit.MILLISECONDS)
                .addTag("pace")
                .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
