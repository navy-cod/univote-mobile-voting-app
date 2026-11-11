package com.univote.evoting.data.repository

import com.univote.evoting.data.local.entity.Role

data class SessionUser(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: Role,
    val voterRegNumber: String?
)

sealed interface LoginResult {
    data class Success(val user: SessionUser) : LoginResult
    data object InvalidCredentials : LoginResult
    data class Locked(val minutesLeft: Long) : LoginResult
    data object AccountDisabled : LoginResult
}

sealed interface AccountResult {
    data object Success : AccountResult
    data class Failure(val message: String) : AccountResult
}

/** The UI only knows this interface. A remote (API) implementation can replace the local one later. */
interface AuthRepository {
    suspend fun login(username: String, password: CharArray): LoginResult
    suspend fun registerVoter(regNumber: String, fullName: String, password: CharArray): AccountResult
    suspend fun recoverPassword(
        regNumber: String,
        fullName: String,
        yearOfStudy: Int,
        newPassword: CharArray
    ): AccountResult
}