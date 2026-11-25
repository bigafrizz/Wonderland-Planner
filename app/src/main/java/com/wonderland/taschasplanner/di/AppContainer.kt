package com.wonderland.taschasplanner.di

import android.content.Context
import com.wonderland.taschasplanner.data.backup.WonderlandBackupManager
import com.wonderland.taschasplanner.data.calendar.GoogleCalendarRepository
import com.wonderland.taschasplanner.data.local.WonderlandDatabase
import com.wonderland.taschasplanner.data.repository.PlannerRepository
import com.wonderland.taschasplanner.data.update.AppUpdateManager

class AppContainer(context: Context) {
    val database: WonderlandDatabase = WonderlandDatabase.getInstance(context)
    val googleCalendarRepository: GoogleCalendarRepository = GoogleCalendarRepository(context)
    val backupManager: WonderlandBackupManager = WonderlandBackupManager(database)
    val updateManager: AppUpdateManager = AppUpdateManager(context)
    val plannerRepository: PlannerRepository = PlannerRepository(
        context = context,
        db = database,
        googleCalendarRepository = googleCalendarRepository
    )
}
