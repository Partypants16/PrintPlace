package com.printplace.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.printplace.app.PrintPlaceApplication
import com.printplace.app.ui.library.LibraryScreen
import com.printplace.app.ui.library.LibraryViewModel

/**
 * Only the library route exists for Milestone 1. Preview/import-progress/AR
 * routes (architecture doc section 16) will be added as their milestones
 * land, rather than stubbed out now.
 */
private object Routes {
    const val LIBRARY = "library"
}

@Composable
fun PrintPlaceNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            val app = LocalContext.current.applicationContext as PrintPlaceApplication
            val viewModel: LibraryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { LibraryViewModel(app.container.modelRepository) }
                },
            )
            LibraryScreen(viewModel = viewModel)
        }
    }
}
