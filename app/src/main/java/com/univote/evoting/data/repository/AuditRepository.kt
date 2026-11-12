package com.univote.evoting.data.repository

import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.AuditLogEntity
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.domain.AuditChain
import com.univote.evoting.domain.AuditCsv
import com.univote.evoting.domain.ChainStatus

sealed interface AuditLoad {
    data class Ready(val entries: List<AuditLogEntity>, val total: Int, val chain: ChainStatus) : AuditLoad
    data class Denied(val message: String) : AuditLoad
}

/** Read-only access to the audit log. There is intentionally no way to edit or delete entries. */
class AuditRepository(
    db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val currentUser: () -> SessionUser?
) {
    private val dao = db.auditLogDao()

    private fun reader(): SessionUser? = currentUser()?.takeIf { it.role in READERS }

    /** Newest entries first, plus the verification result for the WHOLE log. */
    suspend fun load(): AuditLoad {
        reader() ?: return AuditLoad.Denied("You do not have access to the audit records.")
        return AuditLoad.Ready(dao.recent(DISPLAY_LIMIT), dao.count(), AuditChain.verify(dao.all()))
    }

    /** Returns CSV text for the latest entries, or null when the user may not read the log. */
    suspend fun exportCsv(): String? {
        val user = reader() ?: return null
        val rows = dao.recent(EXPORT_LIMIT).reversed()
        audit.log(user.username, "AUDIT_EXPORTED", "rows=${rows.size}")
        return AuditCsv.build(rows)
    }

    private companion object {
        val READERS = setOf(Role.OBSERVER, Role.ELECTION_ADMIN, Role.SYSTEM_ADMIN)
        const val DISPLAY_LIMIT = 500
        const val EXPORT_LIMIT = 500
    }
}