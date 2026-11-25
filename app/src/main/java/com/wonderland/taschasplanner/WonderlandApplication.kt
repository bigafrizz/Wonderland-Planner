package com.wonderland.taschasplanner

import android.app.Application
import com.wonderland.taschasplanner.di.AppContainer

class WonderlandApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
