package com.univote.evoting.core.session

import android.content.Context
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.repository.SessionUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the signed-in user. Stores only identity details and a last-activity time, never a password or hash.
 * The session expires after [SESSION_TIMEOUT_MS] of inactivity.
 */
class SessionManager(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow(restore())
    val currentUser: StateFlow<SessionUser?> = _currentUser.asStateFlow()

    fun start(user: SessionUser) {
        prefs.edit()
            .putLong(KEY_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_NAME, user.displayName)
            .putString(KEY_ROLE, user.role.name)
            .putString(KEY_REG, user.voterRegNumber)
            .putLong(KEY_LAST_ACTIVE, clock())
            .apply()
        _currentUser.value = user
    }

    /** Call on user activity: ends an expired session, otherwise extends it. */
    fun touch() {
        validate()
        if (_currentUser.value != null) prefs.edit().putLong(KEY_LAST_ACTIVE, clock()).apply()
    }

    fun validate() {
        if (_currentUser.value != null && isExpired()) end()
    }

    fun end() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    private fun isExpired() = clock() - prefs.getLong(KEY_LAST_ACTIVE, 0L) > SESSION_TIMEOUT_MS

    private fun restore(): SessionUser? {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id < 0) return null
        if (isExpired()) {
            prefs.edit().clear().apply()
            return null
        }
        val role = prefs.getString(KEY_ROLE, null)
            ?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return SessionUser(id, username, name, role, prefs.getString(KEY_REG, null))
    }

    companion object {
        const val PREFS_NAME = "univote_session"
        const val SESSION_TIMEOUT_MS = 15L * 60 * 1000
        private const val KEY_ID = "id"
        private const val KEY_USERNAME = "username"
        private const val KEY_NAME = "name"
        private const val KEY_ROLE = "role"
        private const val KEY_REG = "reg"
        private const val KEY_LAST_ACTIVE = "last_active"
    }
}