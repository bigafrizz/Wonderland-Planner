package com.wonderland.taschasplanner.di

import android.content.Context
import com.wonderland.taschasplanner.data.backup.AutoBackupStore
import com.wonderland.taschasplanner.data.backup.WonderlandBackupManager
import com.wonderland.taschasplanner.data.calendar.GoogleCalendarRepository
import com.wonderland.taschasplanner.data.local.WonderlandDatabase
import com.wonderland.taschasplanner.data.repository.PlannerRepository
import com.wonderland.taschasplanner.data.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: WonderlandDatabase = WonderlandDatabase.getInstance(appContext)
    val googleCalendarRepository: GoogleCalendarRepository = GoogleCalendarRepository(appContext)
    val backupManager: WonderlandBackupManager = WonderlandBackupManager(database)
    val autoBackupStore: AutoBackupStore = AutoBackupStore(
        context = appContext,
        db = database,
        backupManager = backupManager,
        appScope = applicationScope
    )
    val updateManager: AppUpdateManager = AppUpdateManager(appContext)
    val plannerRepository: PlannerRepository = PlannerRepository(
        context = appContext,
        db = database,
        googleCalendarRepository = googleCalendarRepository,
        autoBackupStore = autoBackupStore,
        appScope = applicationScope
    )
}
