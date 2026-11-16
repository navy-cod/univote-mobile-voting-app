package com.univote.evoting.data.repository

import com.univote.evoting.data.local.entity.ElectionEntity

data class BallotCandidate(val id: Long, val name: String, val party: String, val manifesto: String)

data class BallotPosition(val positionId: Long, val title: String, val candidates: List<BallotCandidate>)

data class BallotForm(
    val electionId: Long,
    val electionTitle: String,
    val endTime: Long,
    val positions: List<BallotPosition>
)

/** Proof that a vote was recorded. It deliberately says nothing about the choices made. */
data class VoteReceipt(
    val code: String,
    val electionTitle: String,
    val castAt: Long,
    val positionCount: Int
)

data class VoterElectionItem(val election: ElectionEntity, val receiptCode: String?)

sealed interface BallotLoadResult {
    data class Ready(val form: BallotForm) : BallotLoadResult
    data class AlreadyVoted(val receipt: VoteReceipt) : BallotLoadResult
    data class Unavailable(val message: String) : BallotLoadResult
}

sealed interface CastVoteResult {
    data class Success(val receipt: VoteReceipt) : CastVoteResult
    data object AlreadyVoted : CastVoteResult
    data class Rejected(val message: String) : CastVoteResult
}