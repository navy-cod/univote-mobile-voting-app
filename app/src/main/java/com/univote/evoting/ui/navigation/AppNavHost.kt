package com.univote.evoting.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.univote.evoting.di.AppContainer
import com.univote.evoting.ui.screens.SetupCheckScreen
import com.univote.evoting.ui.screens.SetupCheckViewModel
import com.univote.evoting.ui.screens.SplashScreen

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                container = container,
                onReady = {
                    navController.navigate(Routes.SETUP_CHECK) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SETUP_CHECK) {
            val viewModel: SetupCheckViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SetupCheckViewModel(container.database) }
                }
            )
            SetupCheckScreen(viewModel = viewModel)
        }
    }
}
