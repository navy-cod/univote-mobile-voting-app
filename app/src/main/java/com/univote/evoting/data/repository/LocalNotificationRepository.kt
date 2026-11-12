package com.univote.evoting.data.repository

import com.univote.evoting.core.notifications.SystemNotifier
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.NotificationType
import com.univote.evoting.data.local.entity.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Room-backed inbox plus system notifications. One object, two narrow interfaces for its two audiences. */
class LocalNotificationRepository(
    db: UniVoteDatabase,
    private val system: SystemNotifier
) : NotificationRepository, NotificationPublisher {

    private val dao = db.notificationDao()
    private val userDao = db.userDao()

    // ---- NotificationRepository (inbox) ----

    override fun observeInbox(userId: Long): Flow<List<NotificationEntity>> = dao.observeForUser(userId)

    override fun observeUnreadCount(userId: Long): Flow<Int> = dao.observeUnreadCount(userId)

    override suspend fun markRead(userId: Long, notificationId: Long) {
        dao.markRead(notificationId, userId)
        system.cancel(notificationId) // read in the app -> remove it from the status bar too
    }

    override suspend fun markAllRead(userId: Long) {
        val ids = dao.unreadIds(userId)
        dao.markAllRead(userId)
        system.cancelAll(ids)
    }

    override suspend fun delete(userId: Long, notificationId: Long) {
        dao.delete(notificationId, userId)
        system.cancel(notificationId)
    }

    override suspend fun clearAll(userId: Long) {
        val ids = dao.allIds(userId)
        dao.deleteAll(userId)
        system.cancelAll(ids)
    }

    // ---- NotificationPublisher ----

    override suspend fun publish(userId: Long, type: NotificationType, title: String, message: String) {
        safely {
            // Save first: the inbox is the source of truth, the system alert is a convenience.
            val id = dao.insert(NotificationEntity(userId = userId, title = title, message = message, type = type))
            system.show(id, type, title, message)
        }
    }

    override suspend fun publishToAllVoters(type: NotificationType, title: String, message: String) {
        safely {
            val voterIds = userDao.findActiveIdsByRole(Role.VOTER)
            if (voterIds.isEmpty()) return@safely
            val now = System.currentTimeMillis()
            val rowIds = dao.insertAll(
                voterIds.map { NotificationEntity(userId = it, title = title, message = message, type = type, createdAt = now) }
            )
            // Everyone shares this device, so raise a single alert rather than one per account.
            // With a real backend, push (FCM) would replace this fan-out.
            system.show(rowIds.first(), type, title, message)
        }
    }

    private suspend inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A notification failure (e.g. user row gone) must never break the caller's flow.
        }
    }
}
