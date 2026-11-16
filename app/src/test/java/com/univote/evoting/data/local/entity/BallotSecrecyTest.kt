package com.univote.evoting.data.local.entity

import org.junit.Assert.assertTrue
import org.junit.Test

class BallotSecrecyTest {
    /** Guards the privacy design: a ballot must never gain a column that could identify the voter or the moment. */
    @Test
    fun ballotEntity_hasNoVoterOrTimeFields() {
        val fields = BallotEntity::class.java.declaredFields.map { it.name.lowercase() }
        val forbidden = listOf("voter", "user", "time", "castat", "votedat", "created")
        assertTrue("Unexpected fields: $fields", fields.none { f -> forbidden.any { it in f } })
    }
}