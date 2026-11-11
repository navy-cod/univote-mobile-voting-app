package com.univote.evoting.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.univote.evoting.di.AppContainer
import com.univote.evoting.ui.auth.LoginScreen
import com.univote.evoting.ui.auth.LoginViewModel
import com.univote.evoting.ui.auth.RecoverPasswordScreen
import com.univote.evoting.ui.auth.RecoverPasswordViewModel
import com.univote.evoting.ui.auth.RegisterScreen
import com.univote.evoting.ui.auth.RegisterViewModel
import com.univote.evoting.ui.home.HomePlaceholderScreen
import com.univote.evoting.ui.screens.SplashScreen

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val sessionUser by container.sessionManager.currentUser.collectAsStateWithLifecycle()

    // Sign-out or an expired session sends the user back to Login.
    LaunchedEffect(sessionUser) {
        if (sessionUser == null && navController.currentDestination?.route == Routes.HOME) {
            navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                container = container,
                onReady = {
                    val destination =
                        if (container.sessionManager.currentUser.value != null) Routes.HOME else Routes.LOGIN
                    navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }

        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { LoginViewModel(container.authRepository, container.sessionManager) }
                }
            )
            LoginScreen(
                viewModel = vm,
                onLoggedIn = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.RECOVER) }
            )
        }

        composable(Routes.REGISTER) {
            val vm: RegisterViewModel = viewModel(
                factory = viewModelFactory { initializer { RegisterViewModel(container.authRepository) } }
            )
            RegisterScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RECOVER) {
            val vm: RecoverPasswordViewModel = viewModel(
                factory = viewModelFactory { initializer { RecoverPasswordViewModel(container.authRepository) } }
            )
            RecoverPasswordScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            sessionUser?.let { user ->
                HomePlaceholderScreen(
                    user = user,
                    onSignOut = { container.sessionManager.end() }
                )
            }
        }
    }
}