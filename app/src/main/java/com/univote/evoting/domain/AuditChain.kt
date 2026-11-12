package com.univote.evoting.domain

import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.entity.AuditLogEntity

sealed interface ChainStatus {
    data class Intact(val entries: Int) : ChainStatus
    data class Broken(val entryId: Long, val reason: String) : ChainStatus
}

object AuditChain {
    /** Re-computes every link. Any edited or removed (non-final) entry makes the chain Broken. */
    fun verify(entries: List<AuditLogEntity>): ChainStatus {
        var previous = AuditLogger.GENESIS_HASH
        entries.sortedBy { it.id }.forEach { e ->
            if (e.previousHash != previous) {
                return ChainStatus.Broken(e.id, "Its link to the previous entry does not match (an entry may have been removed or changed).")
            }
            val expected = AuditLogger.computeHash(e.previousHash, e.timestamp, e.actor, e.action, e.details)
            if (e.entryHash != expected) {
                return ChainStatus.Broken(e.id, "Its contents do not match their recorded fingerprint.")
            }
            previous = e.entryHash
        }
        return ChainStatus.Intact(entries.size)
    }
}

object AuditCsv {
    fun build(entries: List<AuditLogEntity>): String = buildString {
        append("id,time,actor,action,details,fingerprint\n")
        entries.forEach { e ->
            append(
                listOf(e.id.toString(), DateFormats.format(e.timestamp), e.actor, e.action, e.details, e.entryHash)
                    .joinToString(",") { cell(it) }
            ).append('\n')
        }
    }

    /** Quotes the value and neutralises spreadsheet formulas ("=", "+", "-", "@"). */
    fun cell(raw: String): String {
        val safe = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}