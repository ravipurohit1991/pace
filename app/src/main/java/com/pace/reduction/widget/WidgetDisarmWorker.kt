package com.pace.reduction.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pace.reduction.PaceApplication
import java.util.concurrent.TimeUnit

/** Returns the log button to its resting state if the confirm tap never comes. */
class WidgetDisarmWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        (applicationContext as PaceApplication).container.repository.disarmWidgetLog()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        private const val UNIQUE_WORK_NAME = "pace-widget-disarm"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetDisarmWorker>()
                // Slightly past the arming window so the button is definitely stale by then.
                .setInitialDelay(9, TimeUnit.SECONDS)
                .addTag("pace")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
