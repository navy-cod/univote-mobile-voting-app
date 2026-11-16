package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.repository.AuditLoad
import com.univote.evoting.data.repository.AuditRepository
import com.univote.evoting.data.repository.LocalAuthRepository
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.domain.ChainStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditRepositoryTest {

    private lateinit var db: UniVoteDatabase
    private lateinit var logger: AuditLogger

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
        logger = AuditLogger(db)
        repeat(4) { logger.log("admin", "TEST_ACTION_$it", "detail $it") }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun repoFor(username: String): AuditRepository {
        val u = db.userDao().findByUsername(username)!!
        val session = SessionUser(u.id, u.username, u.displayName, u.role, u.voterRegNumber)
        return AuditRepository(db, logger) { session }
    }

    @Test
    fun observer_readsLog_andChainIsIntact() = runBlocking {
        val result = repoFor("observer").load()
        assertTrue(result is AuditLoad.Ready)
        result as AuditLoad.Ready
        assertEquals(4, result.total)
        assertEquals("TEST_ACTION_3", result.entries.first().action) // newest first
        assertEquals(ChainStatus.Intact(4), result.chain)
    }

    @Test
    fun voterAndSupportOfficer_areDenied() = runBlocking {
        assertTrue(repoFor("CS/0001/2024").load() is AuditLoad.Denied)
        assertTrue(repoFor("support").load() is AuditLoad.Denied)
        assertNull(repoFor("CS/0001/2024").exportCsv())
    }

    @Test
    fun tamperedEntry_isReportedAsBroken() = runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE audit_logs SET details = 'edited' WHERE id = 2")
        val ready = repoFor("observer").load() as AuditLoad.Ready
        assertTrue(ready.chain is ChainStatus.Broken)
        assertEquals(2L, (ready.chain as ChainStatus.Broken).entryId)
    }

    @Test
    fun deletedMiddleEntry_isReportedAsBroken() = runBlocking {
        db.openHelper.writableDatabase.execSQL("DELETE FROM audit_logs WHERE id = 2")
        val ready = repoFor("observer").load() as AuditLoad.Ready
        assertTrue(ready.chain is ChainStatus.Broken)
    }

    @Test
    fun export_returnsCsv_andIsItselfAudited() = runBlocking {
        val csv = repoFor("observer").exportCsv()
        assertNotNull(csv)
        assertTrue(csv!!.startsWith("id,time,actor,action,details,fingerprint"))
        assertTrue(csv.contains("TEST_ACTION_0"))
        assertTrue(db.auditLogDao().all().any { it.action == "AUDIT_EXPORTED" && it.actor == "observer" })
    }

    @Test
    fun securityEvents_areLogged() = runBlocking {
        val auth = LocalAuthRepository(db, logger)

        auth.login("admin", "Admin@123".toCharArray())
        auth.login("CS/0001/2024", "Voter@123".toCharArray())
        repeat(5) { auth.login("observer", "wrong".toCharArray()) }
        auth.registerVoter("CS/0002/2024", "Brian Otieno", "NewPass1".toCharArray())
        auth.recoverPassword("CS/0001/2024", "Amina Wanjiru", 2, "Fresh@Pass1".toCharArray())

        val actions = db.auditLogDao().all().map { it.action }
        assertTrue("STAFF_SIGN_IN" in actions)
        assertTrue("ACCOUNT_LOCKED" in actions)
        assertTrue("VOTER_REGISTERED" in actions)
        assertTrue("PASSWORD_RESET" in actions)
        assertEquals(1, actions.count { it == "STAFF_SIGN_IN" }) // the voter's sign-in is not logged
    }
}