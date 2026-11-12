package com.univote.evoting.data.repository

import androidx.room.withTransaction
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.CandidateStatus
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.NotificationType
import com.univote.evoting.data.local.entity.ResultEntity
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.domain.ElectionResults
import com.univote.evoting.domain.PositionResult
import com.univote.evoting.domain.RawCandidate
import com.univote.evoting.domain.ResultsCalculator

sealed interface ResultsLoad {
    data class Ready(val results: ElectionResults, val canPublish: Boolean) : ResultsLoad
    data class Unavailable(val message: String) : ResultsLoad
}

class ResultsRepository(
    private val db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val currentUser: () -> SessionUser?
) {
    private val electionDao = db.electionDao()
    private val positionDao = db.positionDao()
    private val candidateDao = db.candidateDao()
    private val partyDao = db.partyDao()
    private val ballotDao = db.ballotDao()
    private val participationDao = db.participationDao()
    private val resultDao = db.resultDao()
    private val userDao = db.userDao()
    private val notificationDao = db.notificationDao()

    /** Published results are visible to everyone; an unpublished preview only to the election administrator. */
    suspend fun load(electionId: Long): ResultsLoad {
        val user = currentUser() ?: return ResultsLoad.Unavailable("Please sign in again.")
        val election = electionDao.findById(electionId) ?: return ResultsLoad.Unavailable("Election not found.")

        if (election.resultsPublished) return ResultsLoad.Ready(fromSnapshot(election), canPublish = false)
        if (user.role != Role.ELECTION_ADMIN) {
            return ResultsLoad.Unavailable("Official results have not been published yet.")
        }
        if (election.status != ElectionStatus.CLOSED) {
            return ResultsLoad.Unavailable("Votes can only be counted after voting has closed.")
        }
        return ResultsLoad.Ready(count(election), canPublish = true)
    }

    suspend fun publish(electionId: Long): ActionResult {
        val user = currentUser()
        if (user == null || user.role != Role.ELECTION_ADMIN) {
            return ActionResult.Failure("Only an election administrator can publish results.")
        }
        val election = electionDao.findById(electionId) ?: return ActionResult.Failure("Election not found.")
        if (election.resultsPublished) return ActionResult.Failure("These results are already published.")
        if (election.status != ElectionStatus.CLOSED) {
            return ActionResult.Failure("Voting must be closed before results are published.")
        }

        val counted = count(election)
        if (!counted.allReconcile) {
            return ActionResult.Failure(
                "Ballot counts do not match the participation records, so the results cannot be published."
            )
        }

        val now = System.currentTimeMillis()
        val rows = counted.positions.flatMap { p ->
            buildList<ResultEntity> {
                p.candidates.forEach { c ->
                    add(
                        ResultEntity(
                            electionId = electionId, positionId = p.positionId, candidateId = c.candidateId,
                            votes = c.votes, isWinner = c.isWinner, publishedAt = now
                        )
                    )
                }
                add(
                    ResultEntity(
                        electionId = electionId, positionId = p.positionId, candidateId = null,
                        votes = p.abstentions, isWinner = false, publishedAt = now
                    )
                )
            }
        }
        val recipients = userDao.listRecipientIds()

        db.withTransaction {
            resultDao.insertAll(rows)
            electionDao.markResultsPublished(electionId)
            notificationDao.insertAll(
                recipients.map {
                    NotificationEntity(
                        userId = it,
                        title = "Results published",
                        message = "Official results for \"${election.title}\" are now available.",
                        type = NotificationType.RESULTS,
                        createdAt = now
                    )
                }
            )
        }
        audit.log(user.username, "RESULTS_PUBLISHED", "election=$electionId")
        return ActionResult.Success
    }

    // ---- Counting ----

    private suspend fun count(election: ElectionEntity): ElectionResults {
        val positions = positionDao.listByElection(election.id)
        val tally = ballotDao.tally(election.id)
        val turnout = participationDao.turnoutByPosition(election.id).associate { it.positionId to it.total }
        val candidates = candidateDao.listByElection(election.id)
        val parties = partyDao.listAll().associate { it.id to it.name }

        val sections = positions.map { p ->
            val rows = tally.filter { it.positionId == p.id }
            val abstentions = rows.filter { it.candidateId == null }.sumOf { it.votes }
            val votesById = rows.mapNotNull { r -> r.candidateId?.let { it to r.votes } }.toMap()
            val ballotTotal = rows.sumOf { it.votes }

            val raw = candidates
                .filter { it.positionId == p.id && (it.status == CandidateStatus.APPROVED || it.id in votesById) }
                .map { c -> RawCandidate(c.id, c.fullName, partyOf(c, parties), votesById[c.id] ?: 0) }
            val ranked = ResultsCalculator.rank(raw)
            val participants = turnout[p.id] ?: 0

            PositionResult(
                positionId = p.id,
                title = p.title,
                votesCast = participants,
                abstentions = abstentions,
                candidates = ranked.candidates,
                isTie = ranked.isTie,
                reconciles = ballotTotal == participants
            )
        }
        return ElectionResults(election.title, isPublished = false, positions = sections)
    }

    /** Builds the official view from the stored snapshot, not from a fresh recount. */
    private suspend fun fromSnapshot(election: ElectionEntity): ElectionResults {
        val rows = resultDao.listByElection(election.id)
        val positions = positionDao.listByElection(election.id)
        val candidates = candidateDao.listByElection(election.id).associateBy { it.id }
        val parties = partyDao.listAll().associate { it.id to it.name }

        val sections = positions.map { p ->
            val mine = rows.filter { it.positionId == p.id }
            val raw = mine.mapNotNull { r ->
                val c = r.candidateId?.let { candidates[it] } ?: return@mapNotNull null
                RawCandidate(c.id, c.fullName, partyOf(c, parties), r.votes)
            }
            val ranked = ResultsCalculator.rank(raw)
            val winners = mine.filter { it.isWinner }.mapNotNull { it.candidateId }.toSet()
            PositionResult(
                positionId = p.id,
                title = p.title,
                votesCast = mine.sumOf { it.votes },
                abstentions = mine.filter { it.candidateId == null }.sumOf { it.votes },
                candidates = ranked.candidates.map { it.copy(isWinner = it.candidateId in winners) },
                isTie = ranked.isTie,
                reconciles = true
            )
        }
        return ElectionResults(election.title, isPublished = true, positions = sections)
    }

    private fun partyOf(c: CandidateEntity, parties: Map<Long, String>) =
        c.partyId?.let { parties[it] } ?: "Independent"
}