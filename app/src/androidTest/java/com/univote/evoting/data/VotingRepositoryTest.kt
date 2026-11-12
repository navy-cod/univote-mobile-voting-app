package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.repository.BallotLoadResult
import com.univote.evoting.data.repository.CastVoteResult
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.data.repository.VotingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VotingRepositoryTest {

    private lateinit var db: UniVoteDatabase
    private var electionId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
        electionId = db.electionDao().observeAll().first().first().id
        db.electionDao().updateStatus(electionId, ElectionStatus.OPEN)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun repoFor(username: String): VotingRepository {
        val u = db.userDao().findByUsername(username)!!
        val session = SessionUser(u.id, u.username, u.displayName, u.role, u.voterRegNumber)
        return VotingRepository(db, AuditLogger(db)) { session }
    }

    private suspend fun form(repo: VotingRepository) =
        (repo.loadBallot(electionId) as BallotLoadResult.Ready).form

    private suspend fun firstChoices(repo: VotingRepository): Map<Long, Long?> =
        form(repo).positions.associate { it.positionId to it.candidates.first().id }

    @Test
    fun csVoter_seesOnlyOwnDepartmentPositions() = runBlocking {
        val titles = form(repoFor("CS/0001/2024")).positions.map { it.title }
        assertEquals(listOf("President", "Vice President", "Faculty Representative - Computer Science"), titles)
    }

    @Test
    fun ballot_showsOnlyApprovedCandidates() = runBlocking {
        val names = form(repoFor("BBA/0001/2024")).positions.flatMap { p -> p.candidates.map { it.name } }
        assertTrue("Collins Rotich" !in names) // seeded as PENDING
    }

    @Test
    fun castVote_valid_storesParticipationAndBallots_withReceipt() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val result = repo.castVote(electionId, firstChoices(repo))

        assertTrue(result is CastVoteResult.Success)
        val code = (result as CastVoteResult.Success).receipt.code
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code))
        assertEquals(3, db.participationDao().countForElection(electionId))
        assertEquals(3, db.ballotDao().countForElection(electionId))
        assertTrue(db.participationDao().listForVoter(electionId, "CS/0001/2024").all { it.receiptCode == code })
    }

    @Test
    fun castVote_twice_isRejectedAndStoresNothingMore() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val choices = firstChoices(repo)
        repo.castVote(electionId, choices)

        assertEquals(CastVoteResult.AlreadyVoted, repo.castVote(electionId, choices))
        assertEquals(3, db.ballotDao().countForElection(electionId))
        assertTrue(repo.loadBallot(electionId) is BallotLoadResult.AlreadyVoted)
    }

    @Test
    fun abstain_isAllowed() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val allAbstain: Map<Long, Long?> = form(repo).positions.associate { it.positionId to null }
        assertTrue(repo.castVote(electionId, allAbstain) is CastVoteResult.Success)
        assertEquals(3, db.ballotDao().countForElection(electionId))
    }

    @Test
    fun incompleteBallot_isRejected_andNothingStored() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val partial = firstChoices(repo).entries.take(2).associate { it.key to it.value }
        assertTrue(repo.castVote(electionId, partial) is CastVoteResult.Rejected)
        assertEquals(0, db.participationDao().countForElection(electionId))
        assertEquals(0, db.ballotDao().countForElection(electionId))
    }

    @Test
    fun otherDepartmentPosition_isRejected() = runBlocking {
        val csRepo = repoFor("CS/0001/2024")
        val bbaPositions = form(repoFor("BBA/0001/2024")).positions
        val bbaRep = bbaPositions.last()
        val sneaky = firstChoices(csRepo) + (bbaRep.positionId to bbaRep.candidates.first().id)
        assertTrue(csRepo.castVote(electionId, sneaky) is CastVoteResult.Rejected)
    }

    @Test
    fun candidateFromAnotherPosition_isRejected() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val positions = form(repo).positions
        val swapped: Map<Long, Long?> = mapOf(
            positions[0].positionId to positions[1].candidates.first().id, // wrong position
            positions[1].positionId to positions[1].candidates.first().id,
            positions[2].positionId to positions[2].candidates.first().id
        )
        assertTrue(repo.castVote(electionId, swapped) is CastVoteResult.Rejected)
        assertEquals(0, db.ballotDao().countForElection(electionId))
    }

    @Test
    fun pendingCandidate_isRejected() = runBlocking {
        val repo = repoFor("BBA/0001/2024")
        val pending = db.candidateDao().observeByElection(electionId).first().first { it.fullName == "Collins Rotich" }
        val choices = firstChoices(repo).toMutableMap()
        choices[pending.positionId] = pending.id
        assertTrue(repo.castVote(electionId, choices) is CastVoteResult.Rejected)
    }

    @Test
    fun votingClosedSuspendedOrScheduled_isRejected() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val choices = firstChoices(repo)
        listOf(ElectionStatus.SUSPENDED, ElectionStatus.CLOSED, ElectionStatus.SCHEDULED).forEach { status ->
            db.electionDao().updateStatus(electionId, status)
            assertTrue("$status", repo.castVote(electionId, choices) is CastVoteResult.Rejected)
        }
        assertEquals(0, db.ballotDao().countForElection(electionId))
    }

    @Test
    fun afterEndTime_isRejected() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val choices = firstChoices(repo)
        val election = db.electionDao().findById(electionId)!!
        db.electionDao().update(election.copy(endTime = System.currentTimeMillis() - 1_000))
        assertTrue(repo.castVote(electionId, choices) is CastVoteResult.Rejected)
    }

    @Test
    fun ineligibleVoter_isRejected() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        val choices = firstChoices(repo)
        val voter = db.voterDao().findByRegNumber("CS/0001/2024")!!
        db.voterDao().update(voter.copy(isEligible = false))
        assertTrue(repo.castVote(electionId, choices) is CastVoteResult.Rejected)
        assertTrue(repo.loadBallot(electionId) is BallotLoadResult.Unavailable)
    }

    @Test
    fun nonVoter_cannotVoteOrOpenBallot() = runBlocking {
        val admin = repoFor("admin")
        assertTrue(admin.loadBallot(electionId) is BallotLoadResult.Unavailable)
        assertTrue(admin.castVote(electionId, emptyMap()) is CastVoteResult.Rejected)
    }

    @Test
    fun twoVoters_bothCount_independently() = runBlocking {
        val cs = repoFor("CS/0001/2024")
        val bba = repoFor("BBA/0001/2024")
        assertTrue(cs.castVote(electionId, firstChoices(cs)) is CastVoteResult.Success)
        assertTrue(bba.castVote(electionId, firstChoices(bba)) is CastVoteResult.Success)
        assertEquals(6, db.ballotDao().countForElection(electionId))
        assertEquals(6, db.participationDao().countForElection(electionId))
    }

    @Test
    fun voteIsAudited_withoutRevealingChoice() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        repo.castVote(electionId, firstChoices(repo))
        val entry = db.auditLogDao().all().last { it.action == "VOTE_CAST" }
        assertEquals("election=$electionId", entry.details)
    }
}