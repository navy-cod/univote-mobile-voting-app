package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.CandidateStatus
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.repository.ActionResult
import com.univote.evoting.data.repository.ElectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ElectionRepositoryTest {

    private lateinit var db: UniVoteDatabase
    private lateinit var repo: ElectionRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
        repo = ElectionRepository(db, AuditLogger(db)) { "tester" }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seededId() = db.electionDao().observeAll().first().first().id

    private suspend fun newDraft(title: String = "Test"): Long {
        val now = System.currentTimeMillis()
        repo.create(title, "desc", now - 1_000, now + 3_600_000)
        return db.electionDao().observeAll().first().first { it.title == title }.id
    }

    @Test
    fun open_seededElection_succeeds() = runBlocking {
        val id = seededId()
        assertEquals(ActionResult.Success, repo.changeStatus(id, ElectionStatus.OPEN))
        assertEquals(ElectionStatus.OPEN, db.electionDao().findById(id)?.status)
    }

    @Test
    fun setup_isLockedOnceOpen() = runBlocking {
        val id = seededId()
        repo.changeStatus(id, ElectionStatus.OPEN)
        assertTrue(repo.addPosition(id, "Treasurer", null) is ActionResult.Failure)
    }

    @Test
    fun draft_cannotOpenDirectly() = runBlocking {
        val id = newDraft()
        assertTrue(repo.changeStatus(id, ElectionStatus.OPEN) is ActionResult.Failure)
    }

    @Test
    fun publish_withoutPositions_fails() = runBlocking {
        val id = newDraft()
        assertTrue(repo.changeStatus(id, ElectionStatus.SCHEDULED) is ActionResult.Failure)
    }

    @Test
    fun publish_needsApprovedCandidate() = runBlocking {
        val id = newDraft()
        repo.addPosition(id, "Chair", null)
        val positionId = db.positionDao().listByElection(id).first().id
        repo.addCandidate(positionId, null, "Pat Doe", "My plan")
        val candidate = db.candidateDao().observeByElection(id).first().first()
        assertEquals(CandidateStatus.PENDING, candidate.status)

        assertTrue(repo.changeStatus(id, ElectionStatus.SCHEDULED) is ActionResult.Failure)
        repo.setCandidateStatus(candidate.id, CandidateStatus.APPROVED)
        assertEquals(ActionResult.Success, repo.changeStatus(id, ElectionStatus.SCHEDULED))
    }

    @Test
    fun editingApprovedCandidate_resetsToPending() = runBlocking {
        val id = seededId()
        val candidate = db.candidateDao().observeByElection(id).first().first { it.status == CandidateStatus.APPROVED }
        repo.updateCandidate(candidate.id, candidate.partyId, candidate.fullName, "New manifesto")
        assertEquals(CandidateStatus.PENDING, db.candidateDao().findById(candidate.id)?.status)
    }

    @Test
    fun endBeforeStart_isRejected() = runBlocking {
        val now = System.currentTimeMillis()
        assertTrue(repo.create("Bad", "d", now, now - 1_000) is ActionResult.Failure)
    }

    @Test
    fun auditChain_isIntact() = runBlocking {
        val id = newDraft()
        repo.addPosition(id, "Chair", null)
        repo.changeStatus(id, ElectionStatus.SCHEDULED) // fails, but earlier actions are logged

        val logs = db.auditLogDao().all()
        assertTrue(logs.size >= 2)
        var previous = AuditLogger.GENESIS_HASH
        logs.forEach { log ->
            assertEquals(previous, log.previousHash)
            assertEquals(
                AuditLogger.computeHash(log.previousHash, log.timestamp, log.actor, log.action, log.details),
                log.entryHash
            )
            previous = log.entryHash
        }
    }
}