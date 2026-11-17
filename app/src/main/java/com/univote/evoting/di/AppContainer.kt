package com.univote.evoting.di

import android.content.Context
import androidx.room.Room
import com.univote.evoting.core.notify.NotificationCenter
import com.univote.evoting.core.session.SessionManager
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.repository.AuditRepository
import com.univote.evoting.data.repository.AuthRepository
import com.univote.evoting.data.repository.DepartmentRepository
import com.univote.evoting.data.repository.ElectionRepository
import com.univote.evoting.data.repository.FeedbackRepository
import com.univote.evoting.data.repository.LocalAuthRepository
import com.univote.evoting.data.repository.NotificationRepository
import com.univote.evoting.data.repository.PartyRepository
import com.univote.evoting.data.repository.ResultsRepository
import com.univote.evoting.data.repository.StatisticsRepository
import com.univote.evoting.data.repository.SupportRepository
import com.univote.evoting.data.repository.VoterRepository
import com.univote.evoting.data.repository.VotingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual dependency container owned by the Application. Screens only see repositories,
 * so swapping local Room storage for a remote API later changes this file, not the UI.
 */
class AppContainer(context: Context) {

    val database: UniVoteDatabase = Room.databaseBuilder(
        context.applicationContext,
        UniVoteDatabase::class.java,
        UniVoteDatabase.NAME
    ).build()

    val sessionManager = SessionManager(context.applicationContext)
    val auditLogger = AuditLogger(database)

    private val currentUser = { sessionManager.currentUser.value }
    private val currentActor: () -> String = { sessionManager.currentUser.value?.username ?: "system" }

    val authRepository: AuthRepository = LocalAuthRepository(database, auditLogger)
    val departmentRepository = DepartmentRepository(database, auditLogger, currentActor)
    val voterRepository = VoterRepository(database, auditLogger, currentActor)
    val partyRepository = PartyRepository(database, auditLogger, currentActor)
    val electionRepository = ElectionRepository(database, auditLogger, currentActor)
    val votingRepository = VotingRepository(database, auditLogger, currentUser)
    val resultsRepository = ResultsRepository(database, auditLogger, currentUser)
    val statisticsRepository = StatisticsRepository(database)
    val supportRepository = SupportRepository(database, auditLogger, currentUser)
    val auditRepository = AuditRepository(database, auditLogger, currentUser)
    val feedbackRepository = FeedbackRepository(database, auditLogger, currentUser)
    val notificationRepository = NotificationRepository(database, currentUser)

    val notificationCenter = NotificationCenter(context.applicationContext, database, sessionManager)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Called once at app start; the splash screen waits for [isReady]. */
    suspend fun initialize() {
        DatabaseSeeder(database).seedIfEmpty()
        _isReady.value = true
    }
}