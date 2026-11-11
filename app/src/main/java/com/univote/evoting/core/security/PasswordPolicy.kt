package com.univote.evoting.core.security

object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val HINT = "At least 8 characters, with an uppercase letter, a lowercase letter and a digit."

    /** Returns an error message, or null if the password is acceptable. */
    fun validate(password: CharArray): String? = when {
        password.size < MIN_LENGTH -> "Password must be at least $MIN_LENGTH characters."
        password.none { it.isUpperCase() } -> "Password needs an uppercase letter."
        password.none { it.isLowerCase() } -> "Password needs a lowercase letter."
        password.none { it.isDigit() } -> "Password needs a digit."
        else -> null
    }
}