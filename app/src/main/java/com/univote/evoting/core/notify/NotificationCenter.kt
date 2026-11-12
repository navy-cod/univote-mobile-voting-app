package com.univote.evoting.core.notify

import android.content.Context
import com.univote.evoting.core.session.SessionManager
import com.univote.evoting.data.local.UniVoteDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Turns new unread notification rows for the signed-in user into Android notifications.
 * The last delivered id is remembered per user, so nothing is shown twice.
 * (Reaching a user whose app is closed would need a backend and push messaging.)
 */
class NotificationCenter(
    context: Context,
    db: UniVoteDatabase,
    private val session: SessionManager
) {
    private val notifier = SystemNotifier(context)
    private val prefs = context.getSharedPreferences("univote_notified", Context.MODE_PRIVATE)
    private val dao = db.notificationDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        session.currentUser
            .flatMapLatest { user ->
                if (user == null) emptyFlow() else dao.observeUnread(user.id).map { user.id to it }
            }
            .collect { (userId, unread) ->
                val last = prefs.getLong("last_$userId", 0L)
                val fresh = unread.filter { it.id > last }.sortedBy { it.id }
                if (fresh.isEmpty()) return@collect
                fresh.takeLast(MAX_PER_BATCH).forEach { notifier.post(it.id.toInt(), it.title, it.message) }
                prefs.edit().putLong("last_$userId", fresh.last().id).apply()
            }
    }

    private companion object {
        const val MAX_PER_BATCH = 5
    }
}