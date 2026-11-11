package com.univote.evoting.data.repository

import com.univote.evoting.core.security.PasswordHasher
import com.univote.evoting.core.security.PasswordPolicy
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.local.entity.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAuthRepository(db: UniVoteDatabase) : AuthRepository {

    private val userDao = db.userDao()
    private val voterDao = db.voterDao()

    override suspend fun login(username: String, password: CharArray): LoginResult =
        withContext(Dispatchers.Default) {
            val user = userDao.findByUsername(username.trim())
            if (user == null) {
                // Do the same amount of work so response time does not reveal which usernames exist.
                PasswordHasher.hash(password)
                return@withContext LoginResult.InvalidCredentials
            }
            val now = System.currentTimeMillis()
            if (!user.isActive) return@withContext LoginResult.AccountDisabled
            if (user.lockedUntil > now) {
                return@withContext LoginResult.Locked(minutesLeft(user.lockedUntil, now))
            }
            if (PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                userDao.update(user.copy(failedAttempts = 0, lockedUntil = 0L))
                LoginResult.Success(user.toSessionUser())
            } else {
                recordFailure(user, now)
            }
        }

    override suspend fun registerVoter(
        regNumber: String,
        fullName: String,
        password: CharArray
    ): AccountResult = withContext(Dispatchers.Default) {
        val reg = regNumber.trim().uppercase()
        val voter = voterDao.findByRegNumber(reg)
        if (voter == null || !voter.fullName.equals(fullName.trim(), ignoreCase = true)) {
            return@withContext AccountResult.Failure(ROLL_MISMATCH)
        }
        if (!voter.isEligible) {
            return@withContext AccountResult.Failure("This student is not eligible to vote. Contact the election office.")
        }
        if (userDao.findByUsername(reg) != null) {
            return@withContext AccountResult.Failure("An account already exists. Sign in or reset your password.")
        }
        PasswordPolicy.validate(password)?.let { return@withContext AccountResult.Failure(it) }

        val hashed = PasswordHasher.hash(password)
        userDao.insert(
            UserEntity(
                username = reg,
                displayName = voter.fullName,
                role = Role.VOTER,
                passwordHash = hashed.hash,
                salt = hashed.salt,
                voterRegNumber = reg
            )
        )
        AccountResult.Success
    }

    override suspend fun recoverPassword(
        regNumber: String,
        fullName: String,
        yearOfStudy: Int,
        newPassword: CharArray
    ): AccountResult = withContext(Dispatchers.Default) {
        val reg = regNumber.trim().uppercase()
        val voter = voterDao.findByRegNumber(reg)
        val user = userDao.findByUsername(reg)
        if (voter == null || user == null || user.role != Role.VOTER) {
            return@withContext AccountResult.Failure(RECOVERY_FAILED)
        }
        val now = System.currentTimeMillis()
        if (user.lockedUntil > now) {
            return@withContext AccountResult.Failure("Too many attempts. Try again in ${minutesLeft(user.lockedUntil, now)} minute(s).")
        }
        val detailsMatch = voter.fullName.equals(fullName.trim(), ignoreCase = true) &&
                voter.yearOfStudy == yearOfStudy
        if (!detailsMatch) {
            recordFailure(user, now)
            return@withContext AccountResult.Failure(RECOVERY_FAILED)
        }
        PasswordPolicy.validate(newPassword)?.let { return@withContext AccountResult.Failure(it) }

        val hashed = PasswordHasher.hash(newPassword)
        userDao.update(
            user.copy(passwordHash = hashed.hash, salt = hashed.salt, failedAttempts = 0, lockedUntil = 0L)
        )
        AccountResult.Success
    }

    /** Counts a failed attempt and locks the account after [MAX_FAILED_ATTEMPTS]. */
    private suspend fun recordFailure(user: UserEntity, now: Long): LoginResult {
        val attempts = user.failedAttempts + 1
        return if (attempts >= MAX_FAILED_ATTEMPTS) {
            userDao.update(user.copy(failedAttempts = 0, lockedUntil = now + LOCK_DURATION_MS))
            LoginResult.Locked(LOCK_DURATION_MS / 60_000)
        } else {
            userDao.update(user.copy(failedAttempts = attempts))
            LoginResult.InvalidCredentials
        }
    }

    private fun minutesLeft(until: Long, now: Long) = (until - now + 59_999) / 60_000

    private fun UserEntity.toSessionUser() =
        SessionUser(id, username, displayName, role, voterRegNumber)

    private companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCK_DURATION_MS = 5L * 60 * 1000
        const val ROLL_MISMATCH = "Those details do not match the approved student roll."
        const val RECOVERY_FAILED = "We could not verify those details."
    }
}