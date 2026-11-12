package com.univote.evoting.core.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.univote.evoting.MainActivity
import com.univote.evoting.R
import com.univote.evoting.data.local.entity.NotificationType

class AndroidSystemNotifier(context: Context) : SystemNotifier {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled(), which also covers POST_NOTIFICATIONS
    override fun show(id: Long, type: NotificationType, title: String, message: String) {
        // Denied permission or blocked app: the inbox still has the message, so silently skip the alert.
        if (!manager.areNotificationsEnabled()) return

        val notificationId = id.toInt()
        val channel = NotificationChannels.channelFor(type)

        // Tapping opens the inbox. MainActivity only navigates there once the user is signed in.
        val openInbox = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationIntents.EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context, notificationId, openInbox,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Voting is a private matter: on a locked screen show only a generic line, never the details.
        val lockScreenVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_univote)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_private_text))
            .build()

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_univote)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(categoryFor(type))
            .setPriority(priorityFor(type))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; the inbox entry is already saved.
        }
    }

    override fun cancel(id: Long) = manager.cancel(id.toInt())

    private fun categoryFor(type: NotificationType) = when (type) {
        NotificationType.ERROR -> NotificationCompat.CATEGORY_ERROR
        NotificationType.SCHEDULE -> NotificationCompat.CATEGORY_EVENT
        else -> NotificationCompat.CATEGORY_STATUS
    }

    private fun priorityFor(type: NotificationType) = when (type) {
        NotificationType.ERROR, NotificationType.RESULTS -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }
}
