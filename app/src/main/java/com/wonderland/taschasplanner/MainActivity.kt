package com.wonderland.taschasplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonderland.taschasplanner.ui.WonderlandViewModel
import com.wonderland.taschasplanner.ui.navigation.WonderlandApp
import com.wonderland.taschasplanner.ui.theme.WonderlandTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WonderlandApplication

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val vm: WonderlandViewModel = viewModel(
                factory = WonderlandViewModel.Factory(
                    repository = app.container.plannerRepository,
                    backupManager = app.container.backupManager,
                    updateManager = app.container.updateManager
                )
            )
            val state by vm.uiState.collectAsState()

            WonderlandTheme(themeMode = state.themeMode) {
                WonderlandApp(
                    viewModel = vm,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }
}
