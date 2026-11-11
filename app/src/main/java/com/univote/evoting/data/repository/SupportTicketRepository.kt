package com.univote.evoting.data.repository

import com.univote.evoting.data.local.dao.SupportTicketDao
import com.univote.evoting.data.local.entity.SupportTicketEntity
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.local.entity.TicketType
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID

class SupportTicketRepository(private val dao: SupportTicketDao) {
    fun observeForUser(userId: Long): Flow<List<SupportTicketEntity>> = dao.observeForReporter(userId)
    fun observeAll(): Flow<List<SupportTicketEntity>> = dao.observeAll()

    suspend fun submit(
        reporterUserId: Long,
        type: TicketType,
        subject: String,
        description: String
    ): String {
        val reference = "UV-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.ROOT)}"
        dao.insert(
            SupportTicketEntity(
                referenceNo = reference,
                reporterUserId = reporterUserId,
                type = type,
                subject = subject.trim(),
                description = description.trim()
            )
        )
        return reference
    }

    suspend fun updateStatus(ticketId: Long, status: TicketStatus, note: String?) {
        dao.updateStatus(ticketId, status, note?.trim()?.takeIf { it.isNotEmpty() }, System.currentTimeMillis())
    }

    suspend fun findByReference(reference: String): SupportTicketEntity? =
        dao.findByReference(reference.trim().uppercase(Locale.ROOT))
}
