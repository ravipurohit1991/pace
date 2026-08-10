package com.pace.reduction

import android.content.Context
import androidx.room.Room
import com.pace.reduction.core.coach.CoachService
import com.pace.reduction.data.datastore.pacePreferencesDataStore
import com.pace.reduction.data.datastore.widgetSnapshotDataStore
import com.pace.reduction.data.db.PaceDatabase
import com.pace.reduction.data.repository.PaceRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: PaceDatabase = Room.databaseBuilder(
        appContext,
        PaceDatabase::class.java,
        "pace.db",
    ).addMigrations(
        PaceDatabase.MIGRATION_1_2,
        PaceDatabase.MIGRATION_2_3,
        PaceDatabase.MIGRATION_3_4,
    ).build()

    val repository = PaceRepository(
        context = appContext,
        database = database,
        preferencesStore = appContext.pacePreferencesDataStore,
        widgetStore = appContext.widgetSnapshotDataStore,
    )

    val coachService = CoachService(repository)
}
