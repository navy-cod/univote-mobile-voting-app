package com.univote.evoting.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.univote.evoting.ui.audit.AuditLogScreen
import com.univote.evoting.ui.audit.AuditLogViewModel
import com.univote.evoting.ui.auth.LoginScreen
import com.univote.evoting.ui.auth.LoginViewModel
import com.univote.evoting.ui.auth.RecoverPasswordScreen
import com.univote.evoting.ui.auth.RecoverPasswordViewModel
import com.univote.evoting.ui.auth.RegisterScreen
import com.univote.evoting.ui.auth.RegisterViewModel
import com.univote.evoting.ui.home.HomePlaceholderScreen
import com.univote.evoting.ui.insights.AdminInsightsScreen
import com.univote.evoting.ui.insights.InsightsViewModel
import com.univote.evoting.ui.insights.ObserverHomeScreen
import com.univote.evoting.ui.notifications.NotificationsScreen
import com.univote.evoting.ui.notifications.NotificationsViewModel
import com.univote.evoting.ui.results.ResultsScreen
import com.univote.evoting.ui.results.ResultsViewModel
import com.univote.evoting.ui.screens.SplashScreen
import com.univote.evoting.ui.stats.StatisticsScreen
import com.univote.evoting.ui.stats.StatisticsViewModel
import com.univote.evoting.ui.stats.StatisticsScreen
import com.univote.evoting.ui.stats.StatisticsViewModel
import com.univote.evoting.ui.screens.SplashScreen
import com.univote.evoting.ui.voter.BallotScreen
import com.univote.evoting.ui.voter.BallotViewModel
import com.univote.evoting.ui.voter.VoterHomeScreen
import com.univote.evoting.ui.voter.VoterHomeViewModel
import com.univote.evoting.ui.audit.AuditLogScreen
import com.univote.evoting.ui.audit.AuditLogViewModel
import com.univote.evoting.ui.support.NewReportScreen
import com.univote.evoting.ui.support.NewReportViewModel
import com.univote.evoting.ui.support.StaffHomeScreen
import com.univote.evoting.ui.support.SupportMineScreen
import com.univote.evoting.ui.support.SupportMineViewModel
import com.univote.evoting.ui.support.SupportQueueScreen
import com.univote.evoting.ui.support.SupportQueueViewModel
import com.univote.evoting.ui.support.TicketDetailScreen
import com.univote.evoting.ui.support.TicketDetailViewModel
import com.univote.evoting.ui.support.UnreadViewModel
import com.univote.evoting.ui.voter.BallotScreen
import com.univote.evoting.ui.voter.BallotViewModel
import com.univote.evoting.ui.voter.VoterHomeScreen
import com.univote.evoting.ui.voter.VoterHomeViewModel
import com.univote.evoting.ui.support.SupportScreen
import com.univote.evoting.ui.support.SupportViewModel

@Composable
fun AppNavHost(
    container: AppContainer,
    onSignedIn: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val sessionUser by container.sessionManager.currentUser.collectAsStateWithLifecycle()

    // Ask for the notification permission once a user has signed in.
    LaunchedEffect(sessionUser != null) { if (sessionUser != null) onSignedIn() }

    // Sign-out or an expired session sends the user back to Login from any screen.
    LaunchedEffect(sessionUser) {
        val route = navController.currentDestination?.route
        val isPublic = route == null ||
                route in setOf(Routes.SPLASH, Routes.LOGIN, Routes.REGISTER, Routes.RECOVER)
        if (sessionUser == null && !isPublic) {
            navController.navigate(Routes.LOGIN) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }

    LaunchedEffect(sessionUser != null) { if (sessionUser != null) onSignedIn() }
    val isAdmin = sessionUser?.role == Role.ELECTION_ADMIN
    val allowed: (Set<Role>) -> Boolean = { roles -> sessionUser?.role?.let { it in roles } == true }
    val auditRoles = setOf(Role.OBSERVER, Role.ELECTION_ADMIN, Role.SYSTEM_ADMIN)
    val manageRoles = setOf(Role.SUPPORT_OFFICER, Role.ELECTION_ADMIN, Role.SYSTEM_ADMIN)

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
            RegisterScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.RECOVER) {
            val vm: RecoverPasswordViewModel = viewModel(
                factory = viewModelFactory { initializer { RecoverPasswordViewModel(container.authRepository) } }
            )
            RecoverPasswordScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.HOME) {
            sessionUser?.let { user ->
                when (user.role) {
                    Role.ELECTION_ADMIN -> AdminHomeScreen(
                        user = user,
                        onSignOut = { container.sessionManager.end() },
                        onElections = { navController.navigate(Routes.ADMIN_ELECTIONS) },
                        onVoters = { navController.navigate(Routes.ADMIN_VOTERS) },
                        onDepartments = { navController.navigate(Routes.ADMIN_DEPARTMENTS) },
                        onParties = { navController.navigate(Routes.ADMIN_PARTIES) },
                        onInsights = { navController.navigate(Routes.INSIGHTS) },
                        onSupport = { navController.navigate(Routes.SUPPORT_QUEUE) },
                        onAudit = { navController.navigate(Routes.AUDIT) },
                        onAlerts = { navController.navigate(Routes.NOTIFICATIONS) }
                    )
                    Role.VOTER -> {
                        val vm: VoterHomeViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { VoterHomeViewModel(container.votingRepository, container.notificationRepository) }
                            }
                        )
                        VoterHomeScreen(
                            user = user,
                            viewModel = vm,
                            onVote = { id -> navController.navigate(Routes.ballot(id)) },
                            onStats = { id -> navController.navigate(Routes.stats(id)) },
                            onResults = { id -> navController.navigate(Routes.results(id)) },
                            onAlerts = { navController.navigate(Routes.NOTIFICATIONS) },
                            onSupport = { navController.navigate(Routes.SUPPORT_MINE) },
                            onSignOut = { container.sessionManager.end() }
                        )
                    }
                    Role.OBSERVER -> {
                        val vm: InsightsViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { InsightsViewModel(container.electionRepository, container.notificationRepository) }
                            }
                        )
                        ObserverHomeScreen(
                            user = user,
                            viewModel = vm,
                            onStats = { id -> navController.navigate(Routes.stats(id)) },
                            onResults = { id -> navController.navigate(Routes.results(id)) },
                            onAudit = { navController.navigate(Routes.AUDIT) },
                            onSupport = { navController.navigate(Routes.SUPPORT_MINE) },
                            onAlerts = { navController.navigate(Routes.NOTIFICATIONS) },
                            onSignOut = { container.sessionManager.end() }
                        )
                    }
                    Role.SUPPORT_OFFICER, Role.SYSTEM_ADMIN -> {
                        val vm: UnreadViewModel = viewModel(
                            factory = viewModelFactory { initializer { UnreadViewModel(container.notificationRepository) } }
                        )
                        StaffHomeScreen(
                            user = user,
                            viewModel = vm,
                            canAudit = user.role == Role.SYSTEM_ADMIN,
                            onQueue = { navController.navigate(Routes.SUPPORT_QUEUE) },
                            onAudit = { navController.navigate(Routes.AUDIT) },
                            onAlerts = { navController.navigate(Routes.NOTIFICATIONS) },
                            onSignOut = { container.sessionManager.end() }
                        )
                    }
                }
            }
        }

        // ---- Election administrator ----

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

        composable(Routes.INSIGHTS) {
            if (isAdmin) {
                val vm: InsightsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { InsightsViewModel(container.electionRepository, container.notificationRepository) }
                    }
                )
                AdminInsightsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onStats = { id -> navController.navigate(Routes.stats(id)) },
                    onResults = { id -> navController.navigate(Routes.results(id)) }
                )
            }
        }

        // ---- Voting, statistics, results, alerts ----

        composable(
            route = Routes.BALLOT,
            arguments = listOf(navArgument("electionId") { type = NavType.LongType })
        ) { entry ->
            val electionId = entry.arguments?.getLong("electionId") ?: return@composable
            if (sessionUser?.role == Role.VOTER) {
                val vm: BallotViewModel = viewModel(
                    factory = viewModelFactory { initializer { BallotViewModel(electionId, container.votingRepository) } }
                )
                BallotScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(
            route = Routes.STATS,
            arguments = listOf(navArgument("electionId") { type = NavType.LongType })
        ) { entry ->
            val electionId = entry.arguments?.getLong("electionId") ?: return@composable
            if (sessionUser != null) {
                val vm: StatisticsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { StatisticsViewModel(electionId, container.statisticsRepository) }
                    }
                )
                StatisticsScreen(vm, onBack = { navController.popBackStack() })
            }
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

        composable(
            route = Routes.BALLOT,
            arguments = listOf(navArgument("electionId") { type = NavType.LongType })
        ) { entry ->
            val electionId = entry.arguments?.getLong("electionId") ?: return@composable
            if (sessionUser?.role == Role.VOTER) {
                val vm: BallotViewModel = viewModel(
                    factory = viewModelFactory { initializer { BallotViewModel(electionId, container.votingRepository) } }
                )
                BallotScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.INSIGHTS) {
            if (isAdmin) {
                val vm: InsightsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { InsightsViewModel(container.electionRepository, container.notificationRepository) }
                    }
                )
                AdminInsightsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onStats = { id -> navController.navigate(Routes.stats(id)) },
                    onResults = { id -> navController.navigate(Routes.results(id)) }
                )
            }
        }

        composable(
            route = Routes.STATS,
            arguments = listOf(navArgument("electionId") { type = NavType.LongType })
        ) { entry ->
            val electionId = entry.arguments?.getLong("electionId") ?: return@composable
            if (sessionUser != null) {
                val vm: StatisticsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { StatisticsViewModel(electionId, container.statisticsRepository) }
                    }
                )
                StatisticsScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(
            route = Routes.RESULTS,
            arguments = listOf(navArgument("electionId") { type = NavType.LongType })
        ) { entry ->
            val electionId = entry.arguments?.getLong("electionId") ?: return@composable
            if (sessionUser != null) {
                val vm: ResultsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { ResultsViewModel(electionId, container.resultsRepository) }
                    }
                )
                ResultsScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.NOTIFICATIONS) {
            if (sessionUser != null) {
                val vm: NotificationsViewModel = viewModel(
                    factory = viewModelFactory { initializer { NotificationsViewModel(container.notificationRepository) } }
                )
                NotificationsScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        // ---- Audit records, support and fraud reports ----

        composable(Routes.AUDIT) {
            if (allowed(auditRoles)) {
                val vm: AuditLogViewModel = viewModel(
                    factory = viewModelFactory { initializer { AuditLogViewModel(container.auditRepository) } }
                )
                AuditLogScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.SUPPORT_MINE) {
            if (sessionUser != null) {
                val vm: SupportMineViewModel = viewModel(
                    factory = viewModelFactory { initializer { SupportMineViewModel(container.supportRepository) } }
                )
                SupportMineScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate(Routes.SUPPORT_NEW) }
                )
            }
        }

        composable(Routes.SUPPORT_NEW) {
            if (sessionUser != null) {
                val vm: NewReportViewModel = viewModel(
                    factory = viewModelFactory { initializer { NewReportViewModel(container.supportRepository) } }
                )
                NewReportScreen(vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.SUPPORT_QUEUE) {
            if (allowed(manageRoles)) {
                val vm: SupportQueueViewModel = viewModel(
                    factory = viewModelFactory { initializer { SupportQueueViewModel(container.supportRepository) } }
                )
                SupportQueueScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.supportTicket(id)) }
                )
            }
        }

        composable(
            route = Routes.SUPPORT_TICKET,
            arguments = listOf(navArgument("ticketId") { type = NavType.LongType })
        ) { entry ->
            val ticketId = entry.arguments?.getLong("ticketId") ?: return@composable
            if (allowed(manageRoles)) {
                val vm: TicketDetailViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { TicketDetailViewModel(ticketId, container.supportRepository) }
                    }
                )
                TicketDetailScreen(vm, onBack = { navController.popBackStack() })
                    factory = viewModelFactory { initializer { TicketDetailViewModel(ticketId, container.supportRepository) } }
                )
                TicketDetailScreen(vm, onBack = { navController.popBackStack() })
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