package com.univote.evoting.data.repository

import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NotificationRepository(
    db: UniVoteDatabase,
    private val currentUser: () -> SessionUser?
) {
    private val dao = db.notificationDao()

    fun observeMine(): Flow<List<NotificationEntity>> =
        currentUser()?.let { dao.observeForUser(it.id) } ?: flowOf(emptyList())

    fun observeUnreadCount(): Flow<Int> =
        currentUser()?.let { dao.observeUnreadCount(it.id) } ?: flowOf(0)

    /** Scoped to the signed-in user, so one user can never mark another user's alerts as read. */
    suspend fun markRead(id: Long) {
        val user = currentUser() ?: return
        dao.markRead(id, user.id)
    }

    suspend fun markAllRead() {
        val user = currentUser() ?: return
        dao.markAllRead(user.id)
    }
}