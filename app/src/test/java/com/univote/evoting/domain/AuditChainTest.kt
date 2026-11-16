package com.univote.evoting.domain

import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.entity.AuditLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditChainTest {

    private fun chain(n: Int): List<AuditLogEntity> {
        var previous = AuditLogger.GENESIS_HASH
        return (1..n).map { i ->
            val ts = 1_000L * i
            val hash = AuditLogger.computeHash(previous, ts, "admin", "ACTION_$i", "detail $i")
            AuditLogEntity(i.toLong(), ts, "admin", "ACTION_$i", "detail $i", previous, hash).also { previous = hash }
        }
    }

    @Test fun untouchedChain_isIntact() =
        assertEquals(ChainStatus.Intact(5), AuditChain.verify(chain(5)))

    @Test fun emptyLog_isIntact() =
        assertEquals(ChainStatus.Intact(0), AuditChain.verify(emptyList()))

    @Test fun editedDetails_areDetectedAtThatEntry() {
        val tampered = chain(5).toMutableList()
        tampered[2] = tampered[2].copy(details = "changed")
        val result = AuditChain.verify(tampered)
        assertTrue(result is ChainStatus.Broken)
        assertEquals(3L, (result as ChainStatus.Broken).entryId)
    }

    @Test fun removedMiddleEntry_isDetectedAtTheNextEntry() {
        val tampered = chain(5).filterNot { it.id == 3L }
        val result = AuditChain.verify(tampered)
        assertTrue(result is ChainStatus.Broken)
        assertEquals(4L, (result as ChainStatus.Broken).entryId)
    }

    @Test fun removedFirstEntry_isDetected() {
        val result = AuditChain.verify(chain(4).drop(1))
        assertTrue(result is ChainStatus.Broken)
    }

    @Test fun forgedHash_isDetected() {
        val tampered = chain(3).toMutableList()
        tampered[1] = tampered[1].copy(entryHash = "f".repeat(64))
        assertTrue(AuditChain.verify(tampered) is ChainStatus.Broken)
    }

    @Test fun csvCell_quotesAndNeutralisesFormulas() {
        assertEquals("\"say \"\"hi\"\", ok\"", AuditCsv.cell("say \"hi\", ok"))
        assertEquals("\"'=SUM(A1)\"", AuditCsv.cell("=SUM(A1)"))
        assertEquals("\"'@cmd\"", AuditCsv.cell("@cmd"))
        assertEquals("\"plain\"", AuditCsv.cell("plain"))
    }
}