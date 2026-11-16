package com.univote.evoting.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.BallotEntity
import com.univote.evoting.data.local.entity.CandidateStatus
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.NotificationType
import com.univote.evoting.data.local.entity.ParticipationEntity
import com.univote.evoting.data.local.entity.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.security.SecureRandom
import java.util.UUID

class VotingRepository(
    private val db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val currentUser: () -> SessionUser?
) {
    private val electionDao = db.electionDao()
    private val positionDao = db.positionDao()
    private val candidateDao = db.candidateDao()
    private val partyDao = db.partyDao()
    private val voterDao = db.voterDao()
    private val participationDao = db.participationDao()
    private val ballotDao = db.ballotDao()
    private val notificationDao = db.notificationDao()
    private val random = SecureRandom()

    private fun voterReg(): String? = currentUser()?.takeIf { it.role == Role.VOTER }?.voterRegNumber

    /** Every published election, plus the receipt code if this voter has already voted in it. */
    fun observeElections(): Flow<List<VoterElectionItem>> {
        val reg = voterReg() ?: return flowOf(emptyList())
        return combine(electionDao.observeVisible(), participationDao.observeByVoter(reg)) { elections, mine ->
            val codes = mine.groupBy { it.electionId }.mapValues { it.value.first().receiptCode }
            elections.map { VoterElectionItem(it, codes[it.id]) }
        }
    }

    suspend fun loadBallot(electionId: Long): BallotLoadResult {
        val reg = voterReg() ?: return BallotLoadResult.Unavailable("Only registered voters can open a ballot.")
        val voter = voterDao.findByRegNumber(reg)
            ?: return BallotLoadResult.Unavailable("Your voter record was not found.")
        if (!voter.isEligible) return BallotLoadResult.Unavailable("You are not eligible to vote.")
        val election = electionDao.findById(electionId)
            ?: return BallotLoadResult.Unavailable("Election not found.")

        val earlier = participationDao.listForVoter(electionId, reg)
        if (earlier.isNotEmpty()) {
            return BallotLoadResult.AlreadyVoted(
                VoteReceipt(earlier.first().receiptCode, election.title, earlier.first().votedAt, earlier.size)
            )
        }
        availabilityProblem(election, System.currentTimeMillis())?.let {
            return BallotLoadResult.Unavailable(it)
        }

        val positions = positionDao.listForDepartment(electionId, voter.departmentId)
        if (positions.isEmpty()) {
            return BallotLoadResult.Unavailable("There are no positions open to your department in this election.")
        }
        val partyNames = partyDao.listAll().associate { it.id to it.name }
        val form = BallotForm(
            electionId = election.id,
            electionTitle = election.title,
            endTime = election.endTime,
            positions = positions.map { p ->
                BallotPosition(
                    positionId = p.id,
                    title = p.title,
                    candidates = candidateDao.listApprovedByPosition(p.id).map { c ->
                        BallotCandidate(c.id, c.fullName, c.partyId?.let { partyNames[it] } ?: "Independent", c.manifesto)
                    }
                )
            }
        )
        return BallotLoadResult.Ready(form)
    }

    /**
     * @param selections positionId -> candidateId, where a null candidate means "abstain".
     * Every eligible position must appear exactly once.
     */
    suspend fun castVote(electionId: Long, selections: Map<Long, Long?>): CastVoteResult {
        val user = currentUser() ?: return CastVoteResult.Rejected("Please sign in again.")
        val reg = user.voterRegNumber
        if (user.role != Role.VOTER || reg == null) return CastVoteResult.Rejected("Only registered voters can vote.")

        val outcome = try {
            db.withTransaction { castInTransaction(user, reg, electionId, selections) }
        } catch (e: SQLiteConstraintException) {
            // The unique index caught a duplicate that slipped past the check above.
            CastVoteResult.AlreadyVoted
        }
        if (outcome is CastVoteResult.Success) {
            audit.log(reg, "VOTE_CAST", "election=$electionId") // records THAT, never WHAT
        }
        return outcome
    }

    private suspend fun castInTransaction(
        user: SessionUser,
        reg: String,
        electionId: Long,
        selections: Map<Long, Long?>
    ): CastVoteResult {
        val voter = voterDao.findByRegNumber(reg)
            ?: return CastVoteResult.Rejected("Your voter record was not found.")
        if (!voter.isEligible) return CastVoteResult.Rejected("You are not eligible to vote.")

        val election = electionDao.findById(electionId) ?: return CastVoteResult.Rejected("Election not found.")
        val now = System.currentTimeMillis()
        availabilityProblem(election, now)?.let { return CastVoteResult.Rejected(it) }

        if (participationDao.listForVoter(electionId, reg).isNotEmpty()) return CastVoteResult.AlreadyVoted

        val positions = positionDao.listForDepartment(electionId, voter.departmentId)
        if (positions.isEmpty()) return CastVoteResult.Rejected("There are no positions open to your department.")
        if (selections.keys != positions.map { it.id }.toSet()) {
            return CastVoteResult.Rejected("The ballot is incomplete. Choose a candidate or abstain for every position.")
        }
        for ((positionId, candidateId) in selections) {
            if (candidateId == null) continue
            val candidate = candidateDao.findById(candidateId)
            if (candidate == null || candidate.positionId != positionId || candidate.status != CandidateStatus.APPROVED) {
                return CastVoteResult.Rejected("One of your choices is no longer valid. Please review your ballot.")
            }
        }

        val code = newReceiptCode()
        positions.forEach { p ->
            participationDao.insert(
                ParticipationEntity(
                    electionId = electionId, positionId = p.id,
                    voterRegNumber = reg, receiptCode = code, votedAt = now
                )
            )
        }
        // No voter id, no timestamp, random primary key and shuffled order: nothing links a ballot to a voter.
        selections.entries.shuffled().forEach { (positionId, candidateId) ->
            ballotDao.insert(
                BallotEntity(
                    id = randomBallotId(),
                    ballotRef = UUID.randomUUID().toString(),
                    electionId = electionId,
                    positionId = positionId,
                    candidateId = candidateId
                )
            )
        }
        notificationDao.insert(
            NotificationEntity(
                userId = user.id,
                title = "Vote recorded",
                message = "Your vote in \"${election.title}\" was recorded. Receipt code: $code",
                type = NotificationType.VOTE_CONFIRMATION,
                createdAt = now
            )
        )
        return CastVoteResult.Success(VoteReceipt(code, election.title, now, positions.size))
    }

    private fun availabilityProblem(e: ElectionEntity, now: Long): String? = when {
        e.status == ElectionStatus.SUSPENDED -> "Voting is temporarily suspended."
        e.status != ElectionStatus.OPEN -> "Voting is not open for this election."
        now < e.startTime -> "Voting has not started yet."
        now >= e.endTime -> "Voting has ended."
        else -> null
    }

    private fun randomBallotId(): Long = (random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1L)

    private fun newReceiptCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I to avoid misreading
        return (1..3).joinToString("-") {
            (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }
    }
}