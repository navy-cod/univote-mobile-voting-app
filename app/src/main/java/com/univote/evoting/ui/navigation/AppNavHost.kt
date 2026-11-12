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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.di.AppContainer
import com.univote.evoting.ui.admin.AdminHomeScreen
import com.univote.evoting.ui.admin.DepartmentsScreen
import com.univote.evoting.ui.admin.DepartmentsViewModel
import com.univote.evoting.ui.admin.ElectionDetailScreen
import com.univote.evoting.ui.admin.ElectionDetailViewModel
import com.univote.evoting.ui.admin.ElectionsScreen
import com.univote.evoting.ui.admin.ElectionsViewModel
import com.univote.evoting.ui.admin.PartiesScreen
import com.univote.evoting.ui.admin.PartiesViewModel
import com.univote.evoting.ui.admin.VotersScreen
import com.univote.evoting.ui.admin.VotersViewModel
import com.univote.evoting.ui.auth.LoginScreen
import com.univote.evoting.ui.auth.LoginViewModel
import com.univote.evoting.ui.auth.RecoverPasswordScreen
import com.univote.evoting.ui.auth.RecoverPasswordViewModel
import com.univote.evoting.ui.auth.RegisterScreen
import com.univote.evoting.ui.auth.RegisterViewModel
import com.univote.evoting.ui.home.HomePlaceholderScreen
import com.univote.evoting.ui.screens.SplashScreen
import com.univote.evoting.ui.support.SupportScreen
import com.univote.evoting.ui.support.SupportViewModel

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val sessionUser by container.sessionManager.currentUser.collectAsStateWithLifecycle()

    // Sign-out or an expired session sends the user back to Login.
    LaunchedEffect(sessionUser) {
        val route = navController.currentDestination?.route
        val isPublic = route == null ||
                route in setOf(Routes.SPLASH, Routes.LOGIN, Routes.REGISTER, Routes.RECOVER)
        if (sessionUser == null && !isPublic) {
            navController.navigate(Routes.LOGIN) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }
    val isAdmin = sessionUser?.role == Role.ELECTION_ADMIN

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.HOME) {
            sessionUser?.let { user ->
                if (user.role == Role.ELECTION_ADMIN) {
                    AdminHomeScreen(
                        user = user,
                        onSignOut = { container.sessionManager.end() },
                        onElections = { navController.navigate(Routes.ADMIN_ELECTIONS) },
                        onVoters = { navController.navigate(Routes.ADMIN_VOTERS) },
                        onDepartments = { navController.navigate(Routes.ADMIN_DEPARTMENTS) },
                        onParties = { navController.navigate(Routes.ADMIN_PARTIES) }
                    )
                } else {
                    HomePlaceholderScreen(user = user, onSignOut = { container.sessionManager.end() })
                }
            }
        }

        composable(Routes.ADMIN_DEPARTMENTS) {
            if (isAdmin) {
                val vm: DepartmentsViewModel = viewModel(
                    factory = viewModelFactory { initializer { DepartmentsViewModel(container.departmentRepository) } }
                )
                DepartmentsScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.ADMIN_PARTIES) {
            if (isAdmin) {
                val vm: PartiesViewModel = viewModel(
                    factory = viewModelFactory { initializer { PartiesViewModel(container.partyRepository) } }
                )
                PartiesScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.ADMIN_VOTERS) {
            if (isAdmin) {
                val vm: VotersViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { VotersViewModel(container.voterRepository, container.departmentRepository) }
                    }
                )
                VotersScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.ADMIN_ELECTIONS) {
            if (isAdmin) {
                val vm: ElectionsViewModel = viewModel(
                    factory = viewModelFactory { initializer { ElectionsViewModel(container.electionRepository) } }
                )
                ElectionsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenElection = { id -> navController.navigate(Routes.electionDetail(id)) }
                )
            }
        }

        composable(
            route = Routes.ELECTION_DETAIL,
            arguments = listOf(navArgument("electionId") { type = NavType.LongType })
        ) { entry ->
            val electionId = entry.arguments?.getLong("electionId") ?: return@composable
            if (isAdmin) {
                val vm: ElectionDetailViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ElectionDetailViewModel(
                                electionId,
                                container.electionRepository,
                                container.partyRepository,
                                container.departmentRepository
                            )
                        }
                    }
                )
                ElectionDetailScreen(vm, onBack = { navController.popBackStack() })
            }
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
                    onSignOut = { container.sessionManager.end() },
                    onSupport = { navController.navigate(Routes.SUPPORT) }
                )
            }
        }

        composable(Routes.SUPPORT) {
            sessionUser?.let { user ->
                val vm: SupportViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { SupportViewModel(container.supportTicketRepository, user) }
                    }
                )
                SupportScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}