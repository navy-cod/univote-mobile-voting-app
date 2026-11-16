package com.univote.evoting.data.repository

import com.univote.evoting.data.local.entity.NotificationType

/**
 * What the rest of the app (auth, voting, results...) uses to raise a notification.
 * Publishing saves an inbox entry and shows a system notification. It must never throw: a failed
 * notification must not break the action that triggered it.
 */
interface NotificationPublisher {
    suspend fun publish(userId: Long, type: NotificationType, title: String, message: String)

    /** Announces to every active voter account (one inbox entry each, one system alert for the device). */
    suspend fun publishToAllVoters(type: NotificationType, title: String, message: String)

    /** Default for code and tests that do not care about notifications. */
    object None : NotificationPublisher {
        override suspend fun publish(userId: Long, type: NotificationType, title: String, message: String) = Unit
        override suspend fun publishToAllVoters(type: NotificationType, title: String, message: String) = Unit
    }
}

/**
 * Wording lives in one place so tone stays consistent.
 * Never put WHO a person voted for in any message: only that they voted (and the receipt code).
 */
suspend fun NotificationPublisher.accountCreated(userId: Long) = publish(
    userId, NotificationType.CONFIRMATION,
    "Account created",
    "Your voter account is ready. Sign in to see elections you can vote in."
)

suspend fun NotificationPublisher.passwordChanged(userId: Long) = publish(
    userId, NotificationType.CONFIRMATION,
    "Password changed",
    "Your password was changed. If this was not you, contact the election office immediately."
)

suspend fun NotificationPublisher.accountLocked(userId: Long, minutes: Long) = publish(
    userId, NotificationType.ERROR,
    "Account temporarily locked",
    "Too many failed attempts. Your account is locked for $minutes minute(s). If this was not you, tell the election office."
)

suspend fun NotificationPublisher.voteConfirmed(userId: Long, electionTitle: String, receiptCode: String) = publish(
    userId, NotificationType.VOTE_CONFIRMATION,
    "Vote recorded",
    "Your vote in \"$electionTitle\" was recorded. Receipt code: $receiptCode"
)

suspend fun NotificationPublisher.voteFailed(userId: Long, electionTitle: String, reason: String) = publish(
    userId, NotificationType.ERROR,
    "Vote not recorded",
    "We could not record your vote in \"$electionTitle\": $reason You can try again while the election is open."
)

suspend fun NotificationPublisher.resultsPublished(electionTitle: String) = publishToAllVoters(
    NotificationType.RESULTS,
    "Results published",
    "Official results for \"$electionTitle\" are now available."
)
