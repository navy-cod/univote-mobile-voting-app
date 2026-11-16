package com.univote.evoting.ui.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.repository.ResultsLoad
import com.univote.evoting.data.repository.ResultsRepository
import com.univote.evoting.domain.ElectionResults
import com.univote.evoting.domain.PositionResult
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.AdminViewModel
import com.univote.evoting.ui.admin.ConfirmDialog
import com.univote.evoting.ui.auth.ErrorText
import kotlinx.coroutines.launch

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Unavailable(val message: String) : ResultsUiState
    data class Ready(val results: ElectionResults, val canPublish: Boolean) : ResultsUiState
}

class ResultsViewModel(
    private val electionId: Long,
    private val repo: ResultsRepository
) : AdminViewModel() {

    var state by mutableStateOf<ResultsUiState>(ResultsUiState.Loading)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state = when (val r = repo.load(electionId)) {
                is ResultsLoad.Ready -> ResultsUiState.Ready(r.results, r.canPublish)
                is ResultsLoad.Unavailable -> ResultsUiState.Unavailable(r.message)
            }
        }
    }

    fun publish() = perform(
        successText = "Results published. Voters and observers have been notified.",
        onSuccess = { load() }
    ) { repo.publish(electionId) }
}

@Composable
fun ResultsScreen(viewModel: ResultsViewModel, onBack: () -> Unit) {
    val state = viewModel.state
    var confirming by remember { mutableStateOf(false) }

    AdminScaffold(
        title = "Results",
        onBack = onBack,
        message = viewModel.message,
        onMessageShown = viewModel::clearMessage
    ) { padding ->
        when (state) {
            ResultsUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ResultsUiState.Unavailable -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
            is ResultsUiState.Ready -> {
                val results = state.results
                LazyColumn(
                    Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(results.electionTitle, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (results.isPublished) "Official results"
                                else "Preview for administrators. Voters cannot see these until you approve and publish them.",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!results.allReconcile) {
                                ErrorText("Ballot counts do not match the participation records for at least one position. Publishing is disabled until this is investigated.")
                            }
                            if (state.canPublish) {
                                Button(
                                    onClick = { confirming = true },
                                    enabled = results.allReconcile,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) { Text("Approve and publish results") }
                            }
                        }
                    }
                    items(results.positions, key = { it.positionId }) { PositionResultCard(it) }
                }
            }
        }
    }

    if (confirming) {
        ConfirmDialog(
            title = "Publish results?",
            text = "Voters and observers will be notified and the results become official. This cannot be undone.",
            confirmLabel = "Publish",
            onConfirm = { confirming = false; viewModel.publish() },
            onDismiss = { confirming = false }
        )
    }
}

@Composable
private fun PositionResultCard(position: PositionResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(position.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${position.votesCast} voter(s) took part | ${position.abstentions} abstained",
                style = MaterialTheme.typography.bodySmall
            )
            if (!position.reconciles) ErrorText("Counts do not match the participation records.")

            position.candidates.forEach { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${c.name} (${c.party})" + if (c.isWinner) " - WINNER" else "",
                        style = if (c.isWinner) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${c.votes} (${"%.1f".format(c.percent)}%)", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(progress = { (c.percent / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            if (position.isTie) {
                Text(
                    "Tie: no winner is declared automatically. Follow the election rules for a run-off or decision.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (position.candidates.all { it.votes == 0 }) {
                Text("No valid votes were cast for this position.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}