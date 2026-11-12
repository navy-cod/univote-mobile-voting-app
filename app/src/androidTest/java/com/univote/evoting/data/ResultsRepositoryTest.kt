package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.repository.ActionResult
import com.univote.evoting.data.repository.BallotLoadResult
import com.univote.evoting.data.repository.BallotPosition
import com.univote.evoting.data.repository.CastVoteResult
import com.univote.evoting.data.repository.ResultsLoad
import com.univote.evoting.data.repository.ResultsRepository
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.data.repository.VotingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultsRepositoryTest {

    private lateinit var db: UniVoteDatabase
    private var electionId = 0L

    /**
     * President: Daniel 2-0 (clear winner). Vice President: Mercy 1, Peter 1 (tie).
     * CS Rep: Linet 1. BBA Rep: one abstention.
     */
    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
        electionId = db.electionDao().observeAll().first().first().id
        db.electionDao().updateStatus(electionId, ElectionStatus.OPEN)

        vote("CS/0001/2024") { p ->
            when (p.title) {
                "President" -> id(p, "Daniel Kimani")
                "Vice President" -> id(p, "Mercy Chepkemoi")
                else -> id(p, "Linet Awino")
            }
        }
        vote("BBA/0001/2024") { p ->
            when (p.title) {
                "President" -> id(p, "Daniel Kimani")
                "Vice President" -> id(p, "Peter Karanja")
                else -> null
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun session(username: String): SessionUser {
        val u = db.userDao().findByUsername(username)!!
        return SessionUser(u.id, u.username, u.displayName, u.role, u.voterRegNumber)
    }

    private fun id(p: BallotPosition, name: String): Long = p.candidates.first { it.name == name }.id

    private suspend fun vote(username: String, pick: (BallotPosition) -> Long?) {
        val s = session(username)
        val repo = VotingRepository(db, AuditLogger(db)) { s }
        val form = (repo.loadBallot(electionId) as BallotLoadResult.Ready).form
        val selections: Map<Long, Long?> = form.positions.associate { it.positionId to pick(it) }
        assertTrue(repo.castVote(electionId, selections) is CastVoteResult.Success)
    }

    private suspend fun resultsAs(username: String): ResultsRepository {
        val s = session(username)
        return ResultsRepository(db, AuditLogger(db)) { s }
    }

    private suspend fun closeElection() = db.electionDao().updateStatus(electionId, ElectionStatus.CLOSED)

    @Test
    fun admin_cannotCountWhileVotingIsOpen() = runBlocking {
        assertTrue(resultsAs("admin").load(electionId) is ResultsLoad.Unavailable)
    }

    @Test
    fun closedElection_previewCountsWinnersTiesAndAbstentions() = runBlocking {
        closeElection()
        val ready = resultsAs("admin").load(electionId) as ResultsLoad.Ready
        assertTrue(ready.canPublish)
        assertTrue(ready.results.allReconcile)

        val president = ready.results.positions.first { it.title == "President" }
        assertEquals("Daniel Kimani", president.candidates.first().name)
        assertEquals(2, president.candidates.first().votes)
        assertEquals(100.0, president.candidates.first().percent, 0.001)
        assertTrue(president.candidates.first().isWinner)

        val vp = ready.results.positions.first { it.title == "Vice President" }
        assertTrue(vp.isTie)
        assertTrue(vp.candidates.none { it.isWinner })

        val bbaRep = ready.results.positions.first { it.title.endsWith("Business Administration") }
        assertEquals(1, bbaRep.abstentions)
        assertTrue(bbaRep.candidates.all { it.votes == 0 })
    }

    @Test
    fun voter_cannotSeeUnpublishedResults() = runBlocking {
        closeElection()
        assertTrue(resultsAs("CS/0001/2024").load(electionId) is ResultsLoad.Unavailable)
    }

    @Test
    fun publish_storesSnapshot_marksPublished_andNotifiesVoters() = runBlocking {
        closeElection()
        assertEquals(ActionResult.Success, resultsAs("admin").publish(electionId))

        assertTrue(db.electionDao().findById(electionId)!!.resultsPublished)
        assertTrue(db.resultDao().listByElection(electionId).isNotEmpty())

        val voterId = session("CS/0001/2024").id
        val alerts = db.notificationDao().observeForUser(voterId).first()
        assertTrue(alerts.any { it.title == "Results published" })
        assertTrue(db.auditLogDao().all().any { it.action == "RESULTS_PUBLISHED" })
    }

    @Test
    fun afterPublishing_voterSeesTheSameOfficialResults() = runBlocking {
        closeElection()
        resultsAs("admin").publish(electionId)

        val ready = resultsAs("CS/0001/2024").load(electionId) as ResultsLoad.Ready
        assertFalse(ready.canPublish)
        assertTrue(ready.results.isPublished)
        val president = ready.results.positions.first { it.title == "President" }
        assertEquals("Daniel Kimani", president.candidates.first().name)
        assertTrue(president.candidates.first().isWinner)
        assertTrue(ready.results.positions.first { it.title == "Vice President" }.isTie)
    }

    @Test
    fun publishTwice_isRejected() = runBlocking {
        closeElection()
        val admin = resultsAs("admin")
        admin.publish(electionId)
        assertTrue(admin.publish(electionId) is ActionResult.Failure)
    }

    @Test
    fun publishWhileOpen_isRejected() = runBlocking {
        assertTrue(resultsAs("admin").publish(electionId) is ActionResult.Failure)
    }

    @Test
    fun nonAdmin_cannotPublish() = runBlocking {
        closeElection()
        assertTrue(resultsAs("CS/0001/2024").publish(electionId) is ActionResult.Failure)
        assertFalse(db.electionDao().findById(electionId)!!.resultsPublished)
    }

    @Test
    fun mismatchedBallots_blockPublishing() = runBlocking {
        closeElection()
        db.openHelper.writableDatabase.execSQL("DELETE FROM ballots WHERE id = (SELECT MIN(id) FROM ballots)")

        val admin = resultsAs("admin")
        val ready = admin.load(electionId) as ResultsLoad.Ready
        assertFalse(ready.results.allReconcile)
        assertTrue(admin.publish(electionId) is ActionResult.Failure)
        assertFalse(db.electionDao().findById(electionId)!!.resultsPublished)
    }
}