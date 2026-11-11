package com.univote.evoting.core.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordPolicyTest {
    @Test fun validPassword_returnsNull() = assertNull(PasswordPolicy.validate("Voter@123".toCharArray()))
    @Test fun tooShort_isRejected() = assertNotNull(PasswordPolicy.validate("Ab1".toCharArray()))
    @Test fun noUppercase_isRejected() = assertNotNull(PasswordPolicy.validate("password1".toCharArray()))
    @Test fun noLowercase_isRejected() = assertNotNull(PasswordPolicy.validate("PASSWORD1".toCharArray()))
    @Test fun noDigit_isRejected() = assertNotNull(PasswordPolicy.validate("Password".toCharArray()))
}