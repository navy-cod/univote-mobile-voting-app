package com.univote.evoting.di

import android.content.Context
import androidx.room.Room
import com.univote.evoting.core.session.SessionManager
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.repository.AuthRepository
import com.univote.evoting.data.repository.LocalAuthRepository
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
    val authRepository: AuthRepository = LocalAuthRepository(database)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Called once at app start; the splash screen waits for [isReady]. */
    suspend fun initialize() {
        DatabaseSeeder(database).seedIfEmpty()
        _isReady.value = true
    }
}