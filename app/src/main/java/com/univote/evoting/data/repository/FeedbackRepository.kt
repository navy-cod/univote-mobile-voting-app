package com.univote.evoting.data.repository

import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.FeedbackEntity

class FeedbackRepository(
    db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val currentUser: () -> SessionUser?
) {
    private val dao = db.feedbackDao()

    suspend fun submit(rating: Int?, message: String): ActionResult {
        val user = currentUser() ?: return ActionResult.Failure("Please sign in again.")
        val text = message.trim()
        when {
            rating == null || rating !in 1..5 -> return ActionResult.Failure("Choose a rating from 1 to 5.")
            text.length < 5 -> return ActionResult.Failure("Write at least 5 characters.")
            text.length > 1000 -> return ActionResult.Failure("Keep your feedback under 1000 characters.")
        }
        dao.insert(FeedbackEntity(userId = user.id, rating = rating!!, message = text))
        audit.log(user.username, "FEEDBACK_SUBMITTED", "rating=$rating") // never the message text
        return ActionResult.Success
    }
}