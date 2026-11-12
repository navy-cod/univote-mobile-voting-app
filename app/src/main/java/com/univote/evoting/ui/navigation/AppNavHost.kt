package com.univote.evoting.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import android.content.pm.ApplicationInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.univote.evoting.di.AppContainer
import com.univote.evoting.ui.auth.LoginScreen
import com.univote.evoting.ui.auth.LoginViewModel
import com.univote.evoting.ui.auth.RecoverPasswordScreen
import com.univote.evoting.ui.auth.RecoverPasswordViewModel
import com.univote.evoting.ui.auth.RegisterScreen
import com.univote.evoting.ui.auth.RegisterViewModel
import com.univote.evoting.ui.home.HomePlaceholderScreen
import com.univote.evoting.ui.notifications.NotificationsScreen
import com.univote.evoting.ui.notifications.NotificationsViewModel
import com.univote.evoting.ui.notifications.RequestNotificationPermissionOnce
import com.univote.evoting.ui.screens.SplashScreen

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val sessionUser by container.sessionManager.currentUser.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val openInboxRequested by container.openNotificationsRequested.collectAsStateWithLifecycle()
    val isDebuggable =
        (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // A tapped system notification opens the inbox, but only for a signed-in user: until then the request waits.
    LaunchedEffect(openInboxRequested, sessionUser, currentRoute) {
        if (!openInboxRequested || sessionUser == null) return@LaunchedEffect
        when (currentRoute) {
            Routes.HOME -> {
                container.openNotificationsRequested.value = false
                navController.navigate(Routes.NOTIFICATIONS)
            }
            Routes.NOTIFICATIONS -> container.openNotificationsRequested.value = false
        }
    }

    // Sign-out or an expired session sends the user back to Login.
    LaunchedEffect(sessionUser) {
        val route = navController.currentDestination?.route
        if (sessionUser == null && (route == Routes.HOME || route == Routes.NOTIFICATIONS)) {
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
                val unread by remember(user.id) {
                    container.notificationRepository.observeUnreadCount(user.id)
                }.collectAsStateWithLifecycle(initialValue = 0)

                RequestNotificationPermissionOnce()
                HomePlaceholderScreen(
                    user = user,
                    unreadNotifications = unread,
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = { container.sessionManager.end() }
                )
            }
        }

        composable(Routes.NOTIFICATIONS) {
            sessionUser?.let { user ->
                val vm: NotificationsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            NotificationsViewModel(
                                user.id, container.notificationRepository, container.notificationPublisher
                            )
                        }
                    }
                )
                NotificationsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSimulate = if (isDebuggable) vm::simulate else null
                )
            }
        }
    }
}