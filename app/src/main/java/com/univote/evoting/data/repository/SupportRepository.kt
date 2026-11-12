package com.univote.evoting.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.TicketRow
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.NotificationType
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.local.entity.SupportTicketEntity
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.local.entity.TicketType
import com.univote.evoting.domain.TicketWorkflow
import com.univote.evoting.domain.label
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

sealed interface SubmitResult {
    data class Success(val referenceNo: String) : SubmitResult
    data class Failure(val message: String) : SubmitResult
}

class SupportRepository(
    private val db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val currentUser: () -> SessionUser?
) {
    private val dao = db.supportTicketDao()
    private val userDao = db.userDao()
    private val notificationDao = db.notificationDao()
    private val random = SecureRandom()

    private fun canManage(user: SessionUser?) = user != null && user.role in MANAGERS
    private fun manager(): SessionUser? = currentUser()?.takeIf { canManage(it) }

    fun observeMine(): Flow<List<TicketRow>> =
        currentUser()?.let { dao.observeMine(it.id) } ?: flowOf(emptyList())

    fun observeQueue(): Flow<List<TicketRow>> =
        if (canManage(currentUser())) dao.observeAll() else flowOf(emptyList())

    /** A ticket is visible only to its reporter and to support staff. */
    fun observeTicket(id: Long): Flow<TicketRow?> {
        val user = currentUser() ?: return flowOf(null)
        return dao.observeRow(id).map { row -> row?.takeIf { it.reporterUserId == user.id || canManage(user) } }
    }

    suspend fun submit(type: TicketType, subject: String, description: String, evidenceUri: String?): SubmitResult {
        val user = currentUser() ?: return SubmitResult.Failure("Please sign in again.")
        val s = subject.trim()
        val d = description.trim()
        when {
            s.length < 5 -> return SubmitResult.Failure("Enter a subject of at least 5 characters.")
            s.length > 100 -> return SubmitResult.Failure("Keep the subject under 100 characters.")
            d.length < 10 -> return SubmitResult.Failure("Describe the problem in at least 10 characters.")
            d.length > 2000 -> return SubmitResult.Failure("Keep the description under 2000 characters.")
        }

        repeat(MAX_REFERENCE_ATTEMPTS) {
            val ref = newReference()
            try {
                db.withTransaction {
                    dao.insert(
                        SupportTicketEntity(
                            referenceNo = ref, reporterUserId = user.id, type = type,
                            subject = s, description = d, evidenceUri = evidenceUri
                        )
                    )
                    val now = System.currentTimeMillis()
                    val staff = userDao.listIdsByRoles(recipientRoles(type)).filter { it != user.id }
                    val staffTitle = if (type == TicketType.FRAUD) "Fraud report received" else "New support request"
                    notificationDao.insertAll(
                        listOf(
                            NotificationEntity(
                                userId = user.id, title = "Report received",
                                message = "Your report $ref was received. Follow its progress under My reports.",
                                type = NotificationType.SUPPORT, createdAt = now
                            )
                        ) + staff.map {
                            NotificationEntity(
                                userId = it, title = staffTitle, message = "Reference $ref: $s",
                                type = NotificationType.SUPPORT, createdAt = now
                            )
                        }
                    )
                }
                audit.log(user.username, "TICKET_CREATED", "ref=$ref type=${type.name}") // never the report text
                return SubmitResult.Success(ref)
            } catch (e: SQLiteConstraintException) {
                // Reference already used (extremely unlikely): draw another one.
            }
        }
        return SubmitResult.Failure("Could not submit your report. Please try again.")
    }

    suspend fun assignToMe(ticketId: Long): ActionResult {
        val user = manager() ?: return ActionResult.Failure("Only support staff can take a report.")
        val t = dao.findById(ticketId) ?: return ActionResult.Failure("Report not found.")
        if (t.status == TicketStatus.RESOLVED || t.status == TicketStatus.CLOSED) {
            return ActionResult.Failure("This report is already ${t.status.label().lowercase()}.")
        }
        dao.update(
            t.copy(assignedToUserId = user.id, status = TicketStatus.IN_REVIEW, updatedAt = System.currentTimeMillis())
        )
        if (t.status == TicketStatus.OPEN) notifyReporter(t, "is now being reviewed.")
        audit.log(user.username, "TICKET_ASSIGNED", "ref=${t.referenceNo}")
        return ActionResult.Success
    }

    suspend fun changeStatus(ticketId: Long, target: TicketStatus, note: String): ActionResult {
        val user = manager() ?: return ActionResult.Failure("Only support staff can update a report.")
        val t = dao.findById(ticketId) ?: return ActionResult.Failure("Report not found.")
        if (!TicketWorkflow.canTransition(t.status, target)) {
            return ActionResult.Failure("A report cannot move from ${t.status.label()} to ${target.label()}.")
        }
        val cleaned = note.trim()
        if (TicketWorkflow.requiresNote(target) && cleaned.length < 5) {
            return ActionResult.Failure("Write a short note explaining the outcome.")
        }
        dao.update(
            t.copy(
                status = target,
                resolutionNote = cleaned.ifBlank { t.resolutionNote },
                assignedToUserId = t.assignedToUserId ?: user.id,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyReporter(
            t,
            "is now ${target.label().lowercase()}." + if (cleaned.isNotBlank()) " Note: $cleaned" else ""
        )
        audit.log(user.username, "TICKET_STATUS_CHANGED", "ref=${t.referenceNo} from=${t.status} to=$target")
        return ActionResult.Success
    }

    private suspend fun notifyReporter(t: SupportTicketEntity, text: String) {
        notificationDao.insert(
            NotificationEntity(
                userId = t.reporterUserId,
                title = "Report ${t.referenceNo} updated",
                message = "Your report ${t.referenceNo} $text",
                type = NotificationType.SUPPORT
            )
        )
    }

    private fun recipientRoles(type: TicketType): List<String> =
        if (type == TicketType.FRAUD) {
            listOf(Role.SUPPORT_OFFICER.name, Role.ELECTION_ADMIN.name, Role.SYSTEM_ADMIN.name)
        } else {
            listOf(Role.SUPPORT_OFFICER.name)
        }

    private fun newReference(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "SR-" + (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private companion object {
        val MANAGERS = setOf(Role.SUPPORT_OFFICER, Role.ELECTION_ADMIN, Role.SYSTEM_ADMIN)
        const val MAX_REFERENCE_ATTEMPTS = 3
    }
}