package com.univote.evoting.ui.voter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.repository.BallotForm
import com.univote.evoting.data.repository.BallotLoadResult
import com.univote.evoting.data.repository.CastVoteResult
import com.univote.evoting.data.repository.VoteReceipt
import com.univote.evoting.data.repository.VotingRepository
import kotlinx.coroutines.launch

sealed interface BallotUiState {
    data object Loading : BallotUiState
    data class Unavailable(val message: String) : BallotUiState
    data class AlreadyVoted(val receipt: VoteReceipt) : BallotUiState
    data class Voting(
        val form: BallotForm,
        /** positionId -> candidateId. A null candidate = abstain; a missing key = not decided yet. */
        val selections: Map<Long, Long?> = emptyMap(),
        val reviewing: Boolean = false,
        val isSubmitting: Boolean = false,
        val error: String? = null
    ) : BallotUiState {
        val allDecided: Boolean get() = form.positions.all { selections.containsKey(it.positionId) }
    }
    data class Done(val receipt: VoteReceipt) : BallotUiState
}

/** Selections live only in memory until the final submit, so nothing about a choice is stored early. */
class BallotViewModel(
    private val electionId: Long,
    private val repo: VotingRepository
) : ViewModel() {

    var state by mutableStateOf<BallotUiState>(BallotUiState.Loading)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state = when (val result = repo.loadBallot(electionId)) {
                is BallotLoadResult.Ready -> BallotUiState.Voting(result.form)
                is BallotLoadResult.AlreadyVoted -> BallotUiState.AlreadyVoted(result.receipt)
                is BallotLoadResult.Unavailable -> BallotUiState.Unavailable(result.message)
            }
        }
    }

    fun select(positionId: Long, candidateId: Long?) {
        val s = state as? BallotUiState.Voting ?: return
        if (s.isSubmitting) return
        state = s.copy(selections = s.selections + (positionId to candidateId), error = null)
    }

    fun review() {
        val s = state as? BallotUiState.Voting ?: return
        state = if (s.allDecided) s.copy(reviewing = true, error = null)
        else s.copy(error = "Choose an option for every position.")
    }

    fun backToEdit() {
        val s = state as? BallotUiState.Voting ?: return
        if (!s.isSubmitting) state = s.copy(reviewing = false)
    }

    fun submit() {
        val s = state as? BallotUiState.Voting ?: return
        if (s.isSubmitting || !s.allDecided) return // blocks double taps
        state = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = repo.castVote(electionId, s.selections)) {
                is CastVoteResult.Success -> state = BallotUiState.Done(result.receipt)
                CastVoteResult.AlreadyVoted -> load()
                is CastVoteResult.Rejected ->
                    state = s.copy(isSubmitting = false, reviewing = false, error = result.message)
            }
        }
    }
}