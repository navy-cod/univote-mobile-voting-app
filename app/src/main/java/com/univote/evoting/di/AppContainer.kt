package com.univote.evoting.di

import android.content.Context
import androidx.room.Room
import com.univote.evoting.core.notifications.AndroidSystemNotifier
import com.univote.evoting.core.notifications.SystemNotifier
import com.univote.evoting.core.session.SessionManager
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.repository.AuthRepository
import com.univote.evoting.data.repository.LocalAuthRepository
import com.univote.evoting.data.repository.LocalNotificationRepository
import com.univote.evoting.data.repository.NotificationPublisher
import com.univote.evoting.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual dependency container (a "service locator" owned by the Application).
 * Later phases add repositories here. Screens only ever see repository interfaces,
 * so swapping local Room storage for a remote API later changes this file, not the UI.
 */
class AppContainer(context: Context) {

    val database: UniVoteDatabase = Room.databaseBuilder(
        context.applicationContext,
        UniVoteDatabase::class.java,
        UniVoteDatabase.NAME
    ).build()

    val sessionManager = SessionManager(context.applicationContext)
    val systemNotifier: SystemNotifier = AndroidSystemNotifier(context)
    private val notificationStore = LocalNotificationRepository(database, systemNotifier)

    /** Inbox operations, for the notification screens. */
    val notificationRepository: NotificationRepository = notificationStore

    /** Raise notifications from anywhere (auth, voting, results). Hand this to repositories that need it. */
    val notificationPublisher: NotificationPublisher = notificationStore

    val authRepository: AuthRepository = LocalAuthRepository(database, notificationPublisher)

    /** Set when a system notification was tapped; the nav host opens the inbox once the user is signed in. */
    val openNotificationsRequested = MutableStateFlow(false)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Called once at app start; the splash screen waits for [isReady]. */
    suspend fun initialize() {
        DatabaseSeeder(database).seedIfEmpty()
        _isReady.value = true
    }
}