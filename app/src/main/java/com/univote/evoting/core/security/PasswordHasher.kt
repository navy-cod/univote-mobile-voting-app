package com.univote.evoting.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 (HMAC-SHA256) password hashing. Plaintext passwords are never stored (SRS section 8, item 21).
 * Requires API 26+.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    data class HashResult(val hash: String, val salt: String)

    fun hash(password: CharArray): HashResult {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        return HashResult(
            hash = Base64.getEncoder().encodeToString(hash),
            salt = Base64.getEncoder().encodeToString(salt)
        )
    }

    fun verify(password: CharArray, saltBase64: String, expectedHashBase64: String): Boolean {
        val salt = Base64.getDecoder().decode(saltBase64)
        val expected = Base64.getDecoder().decode(expectedHashBase64)
        val actual = derive(password, salt)
        // Constant-time comparison avoids timing attacks.
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
