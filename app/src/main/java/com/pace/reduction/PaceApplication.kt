package com.pace.reduction

import android.app.Application
import com.pace.reduction.core.notifications.PaceNotifications
import com.pace.reduction.worker.PaceWorkScheduler

class PaceApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        PaceNotifications.createChannels(this)
        PaceWorkScheduler.schedule(this)
    }
}
