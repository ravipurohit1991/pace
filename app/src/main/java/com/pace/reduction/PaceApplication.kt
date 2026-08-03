package com.pace.reduction

import android.app.Application
import com.pace.reduction.core.notifications.PaceNotifications
import com.pace.reduction.data.seed.ProvisioningSeeder
import com.pace.reduction.worker.PaceWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaceApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PaceNotifications.createChannels(this)
        PaceWorkScheduler.schedule(this)
        applicationScope.launch {
            runCatching { ProvisioningSeeder.applyIfNeeded(container.repository) }
        }
    }
}
