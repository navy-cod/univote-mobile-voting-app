package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.repository.AccountResult
import com.univote.evoting.data.repository.LocalAuthRepository
import com.univote.evoting.data.repository.LoginResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthRepositoryTest {

    private lateinit var db: UniVoteDatabase
    private lateinit var repo: LocalAuthRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
        repo = LocalAuthRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun login(user: String, pass: String) = repo.login(user, pass.toCharArray())

    @Test
    fun login_validCredentials_succeedsWithRole() = runBlocking {
        val result = login("admin", "Admin@123")
        assertTrue(result is LoginResult.Success)
        assertEquals(Role.ELECTION_ADMIN, (result as LoginResult.Success).user.role)
    }

    @Test
    fun login_usernameIsCaseInsensitive() = runBlocking {
        assertTrue(login("ADMIN", "Admin@123") is LoginResult.Success)
    }

    @Test
    fun login_wrongPassword_isRejected() = runBlocking {
        assertEquals(LoginResult.InvalidCredentials, login("admin", "wrong"))
    }

    @Test
    fun login_unknownUser_isRejected() = runBlocking {
        assertEquals(LoginResult.InvalidCredentials, login("nobody", "Admin@123"))
    }

    @Test
    fun login_fiveWrongAttempts_locksEvenCorrectPassword() = runBlocking {
        repeat(4) { assertEquals(LoginResult.InvalidCredentials, login("observer", "bad")) }
        assertTrue(login("observer", "bad") is LoginResult.Locked)
        assertTrue(login("observer", "Observer@123") is LoginResult.Locked)
    }

    @Test
    fun register_matchingRoll_createsAccountThatCanLogIn() = runBlocking {
        val r = repo.registerVoter("CS/0002/2024", "Brian Otieno", "NewPass1".toCharArray())
        assertEquals(AccountResult.Success, r)
        assertTrue(login("cs/0002/2024", "NewPass1") is LoginResult.Success)
    }

    @Test
    fun register_notOnRoll_fails() = runBlocking {
        val r = repo.registerVoter("XYZ/1/2099", "Nobody Here", "NewPass1".toCharArray())
        assertTrue(r is AccountResult.Failure)
    }

    @Test
    fun register_wrongName_fails() = runBlocking {
        val r = repo.registerVoter("CS/0002/2024", "Someone Else", "NewPass1".toCharArray())
        assertTrue(r is AccountResult.Failure)
    }

    @Test
    fun register_ineligibleVoter_fails() = runBlocking {
        val r = repo.registerVoter("HS/0002/2022", "Lawrence Omondi", "NewPass1".toCharArray())
        assertTrue(r is AccountResult.Failure)
    }

    @Test
    fun register_existingAccount_fails() = runBlocking {
        val r = repo.registerVoter("CS/0001/2024", "Amina Wanjiru", "NewPass1".toCharArray())
        assertTrue(r is AccountResult.Failure)
    }

    @Test
    fun register_weakPassword_fails() = runBlocking {
        val r = repo.registerVoter("CS/0003/2023", "Cynthia Achieng", "abc".toCharArray())
        assertTrue(r is AccountResult.Failure)
    }

    @Test
    fun recover_correctDetails_changesPassword() = runBlocking {
        val r = repo.recoverPassword("CS/0001/2024", "Amina Wanjiru", 2, "Fresh@Pass1".toCharArray())
        assertEquals(AccountResult.Success, r)
        assertEquals(LoginResult.InvalidCredentials, login("CS/0001/2024", "Voter@123"))
        assertTrue(login("CS/0001/2024", "Fresh@Pass1") is LoginResult.Success)
    }

    @Test
    fun recover_wrongYear_fails() = runBlocking {
        val r = repo.recoverPassword("CS/0001/2024", "Amina Wanjiru", 4, "Fresh@Pass1".toCharArray())
        assertTrue(r is AccountResult.Failure)
    }
}