package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.repository.ActionResult
import com.univote.evoting.data.repository.VoterRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoterRepositoryTest {

    private lateinit var db: UniVoteDatabase
    private lateinit var repo: VoterRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
        repo = VoterRepository(db, AuditLogger(db)) { "tester" }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun importCsv_addsValidRows_skipsDuplicates_reportsErrors() = runBlocking {
        val csv = """
            regNumber,fullName,departmentCode,yearOfStudy
            ENG/0003/2024,Peter Mwangi,ENG,2
            EDU/0003/2024,Rose Akinyi,EDU,1
            CS/0001/2024,Amina Wanjiru,CS,2
            XXX/0001/2024,Ghost Student,NOPE,2
            HS/0003/2024,Bad Year,HS,9
        """.trimIndent()

        val summary = repo.importCsv(csv)

        assertEquals(2, summary.added)
        assertEquals(1, summary.skipped)
        assertEquals(2, summary.errors.size)
        assertEquals("Peter Mwangi", db.voterDao().findByRegNumber("ENG/0003/2024")?.fullName)
    }

    @Test
    fun add_duplicateRegNumber_fails() = runBlocking {
        val deptId = db.departmentDao().findByCode("CS")!!.id
        assertTrue(repo.add("cs/0001/2024", "Someone", deptId, 2, true) is ActionResult.Failure)
    }

    @Test
    fun setEligible_false_marksVoterIneligible() = runBlocking {
        assertEquals(ActionResult.Success, repo.setEligible("CS/0001/2024", false))
        assertEquals(false, db.voterDao().findByRegNumber("CS/0001/2024")?.isEligible)
    }
}