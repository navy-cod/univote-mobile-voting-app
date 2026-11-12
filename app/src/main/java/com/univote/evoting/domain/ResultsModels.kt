package com.univote.evoting.domain

data class CandidateResult(
    val candidateId: Long,
    val name: String,
    val party: String,
    val votes: Int,
    /** Share of valid (non-abstain) votes for this position. */
    val percent: Double,
    val isWinner: Boolean
)

data class PositionResult(
    val positionId: Long,
    val title: String,
    /** Voters who took part for this position, including those who abstained. */
    val votesCast: Int,
    val abstentions: Int,
    val candidates: List<CandidateResult>,
    val isTie: Boolean,
    /** True when ballots counted equals participation records. */
    val reconciles: Boolean
)

data class ElectionResults(
    val electionTitle: String,
    val isPublished: Boolean,
    val positions: List<PositionResult>
) {
    val allReconcile: Boolean get() = positions.all { it.reconciles }
}