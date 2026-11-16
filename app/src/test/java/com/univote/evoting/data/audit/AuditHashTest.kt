package com.univote.evoting.data.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AuditHashTest {
    private val genesis = AuditLogger.GENESIS_HASH

    @Test fun sameInput_givesSameHash() {
        val a = AuditLogger.computeHash(genesis, 1L, "admin", "X", "d")
        val b = AuditLogger.computeHash(genesis, 1L, "admin", "X", "d")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }

    @Test fun changingAnyField_changesHash() {
        val base = AuditLogger.computeHash(genesis, 1L, "admin", "X", "d")
        assertNotEquals(base, AuditLogger.computeHash(genesis, 2L, "admin", "X", "d"))
        assertNotEquals(base, AuditLogger.computeHash(genesis, 1L, "other", "X", "d"))
        assertNotEquals(base, AuditLogger.computeHash(genesis, 1L, "admin", "Y", "d"))
        assertNotEquals(base, AuditLogger.computeHash(genesis, 1L, "admin", "X", "e"))
        assertNotEquals(base, AuditLogger.computeHash("f".repeat(64), 1L, "admin", "X", "d"))
    }
}