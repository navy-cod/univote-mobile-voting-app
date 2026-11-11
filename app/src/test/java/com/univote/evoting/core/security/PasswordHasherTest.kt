package com.univote.evoting.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun verify_correctPassword_returnsTrue() {
        val result = PasswordHasher.hash("Voter@123".toCharArray())
        assertTrue(PasswordHasher.verify("Voter@123".toCharArray(), result.salt, result.hash))
    }

    @Test
    fun verify_wrongPassword_returnsFalse() {
        val result = PasswordHasher.hash("Voter@123".toCharArray())
        assertFalse(PasswordHasher.verify("voter@123".toCharArray(), result.salt, result.hash))
    }

    @Test
    fun hash_samePasswordTwice_usesDifferentSaltsAndHashes() {
        val a = PasswordHasher.hash("Same@Password1".toCharArray())
        val b = PasswordHasher.hash("Same@Password1".toCharArray())
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun hash_neverContainsPlaintext() {
        val result = PasswordHasher.hash("Secret@999".toCharArray())
        assertFalse(result.hash.contains("Secret@999"))
    }
}
