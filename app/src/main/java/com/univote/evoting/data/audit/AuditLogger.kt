package com.univote.evoting.data.audit

import androidx.room.withTransaction
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.AuditLogEntity
import java.security.MessageDigest

/** Appends tamper-evident entries: each one stores a SHA-256 hash that covers the previous entry's hash. */
class AuditLogger(private val db: UniVoteDatabase) {

    suspend fun log(actor: String, action: String, details: String = "") {
        db.withTransaction {
            val dao = db.auditLogDao()
            val previous = dao.last()?.entryHash ?: GENESIS_HASH
            val timestamp = System.currentTimeMillis()
            dao.insert(
                AuditLogEntity(
                    timestamp = timestamp,
                    actor = actor,
                    action = action,
                    details = details,
                    previousHash = previous,
                    entryHash = computeHash(previous, timestamp, actor, action, details)
                )
            )
        }
    }

    companion object {
        val GENESIS_HASH = "0".repeat(64)

        fun computeHash(
            previousHash: String,
            timestamp: Long,
            actor: String,
            action: String,
            details: String
        ): String {
            val input = listOf(previousHash, timestamp, actor, action, details).joinToString("|")
            return MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}