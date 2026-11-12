package com.univote.evoting.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.univote.evoting.R
import com.univote.evoting.data.local.entity.NotificationType

/**
 * One channel per kind of message, so students can mute e.g. general notices without losing
 * result announcements or security alerts. Channel creation is idempotent; call it at every app start.
 */
object NotificationChannels {
    const val CONFIRMATIONS = "confirmations"
    const val ERRORS = "errors"
    const val RESULTS = "results"
    const val GENERAL = "general"

    fun channelFor(type: NotificationType): String = when (type) {
        NotificationType.VOTE_CONFIRMATION, NotificationType.CONFIRMATION -> CONFIRMATIONS
        NotificationType.ERROR -> ERRORS
        NotificationType.RESULTS -> RESULTS
        NotificationType.INFO, NotificationType.SCHEDULE, NotificationType.SUPPORT -> GENERAL
    }

    fun register(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(context, CONFIRMATIONS, R.string.channel_confirmations_name, R.string.channel_confirmations_desc, NotificationManager.IMPORTANCE_DEFAULT),
                channel(context, ERRORS, R.string.channel_errors_name, R.string.channel_errors_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(context, RESULTS, R.string.channel_results_name, R.string.channel_results_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(context, GENERAL, R.string.channel_general_name, R.string.channel_general_desc, NotificationManager.IMPORTANCE_DEFAULT)
            )
        )
    }

    private fun channel(context: Context, id: String, name: Int, desc: Int, importance: Int) =
        NotificationChannel(id, context.getString(name), importance).apply {
            description = context.getString(desc)
        }
}
