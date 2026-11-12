package com.univote.evoting.data.repository

import com.univote.evoting.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/** What the inbox UI needs. Every call is scoped to one user. */
interface NotificationRepository {
    fun observeInbox(userId: Long): Flow<List<NotificationEntity>>
    fun observeUnreadCount(userId: Long): Flow<Int>
    suspend fun markRead(userId: Long, notificationId: Long)
    suspend fun markAllRead(userId: Long)
    suspend fun delete(userId: Long, notificationId: Long)
    suspend fun clearAll(userId: Long)
}
