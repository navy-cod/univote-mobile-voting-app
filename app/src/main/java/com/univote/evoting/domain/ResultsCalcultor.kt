package com.univote.evoting.domain

data class RawCandidate(val id: Long, val name: String, val party: String, val votes: Int)
data class RankedCandidates(val candidates: List<CandidateResult>, val isTie: Boolean)

/** Pure counting rules (no database), so they are easy to unit test. */
object ResultsCalculator {

    fun rank(raw: List<RawCandidate>): RankedCandidates {
        val sorted = raw.sortedWith(compareByDescending<RawCandidate> { it.votes }.thenBy { it.name })
        val validVotes = sorted.sumOf { it.votes }
        val top = sorted.firstOrNull()?.votes ?: 0
        // A winner exists only when someone has votes and nobody else shares the top score.
        val leaders = if (top > 0) sorted.count { it.votes == top } else 0
        return RankedCandidates(
            candidates = sorted.map { r ->
                CandidateResult(
                    candidateId = r.id,
                    name = r.name,
                    party = r.party,
                    votes = r.votes,
                    percent = if (validVotes > 0) r.votes * 100.0 / validVotes else 0.0,
                    isWinner = leaders == 1 && r.votes == top
                )
            },
            isTie = leaders > 1
        )
    }
}