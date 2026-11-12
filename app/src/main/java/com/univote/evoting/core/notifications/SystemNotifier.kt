package com.univote.evoting.core.notifications

import com.univote.evoting.data.local.entity.NotificationType

/**
 * Posts and withdraws Android system (status bar) notifications.
 * An interface so repositories can be tested with a fake, and so a push (FCM) implementation can
 * be added later without touching the callers.
 */
interface SystemNotifier {
    /** [id] is the inbox row id, so an inbox item and its system notification always share one id. */
    fun show(id: Long, type: NotificationType, title: String, message: String)
    fun cancel(id: Long)

    fun cancelAll(ids: List<Long>) = ids.forEach(::cancel)

    /** Used by tests and by [com.univote.evoting.data.repository.LocalNotificationRepository] when system alerts are not wanted. */
    object None : SystemNotifier {
        override fun show(id: Long, type: NotificationType, title: String, message: String) = Unit
        override fun cancel(id: Long) = Unit
    }
}

/** Contract between a tapped system notification and [com.univote.evoting.MainActivity]. */
object NotificationIntents {
    const val EXTRA_OPEN_NOTIFICATIONS = "com.univote.evoting.extra.OPEN_NOTIFICATIONS"
}
