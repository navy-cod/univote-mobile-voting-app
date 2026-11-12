package com.univote.evoting.ui.voter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.data.repository.VoterElectionItem
import com.univote.evoting.data.repository.VotingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class VoterHomeViewModel(repo: VotingRepository) : ViewModel() {
    val elections: StateFlow<List<VoterElectionItem>> =
        repo.observeElections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoterHomeScreen(
    user: SessionUser,
    viewModel: VoterHomeViewModel,
    onVote: (Long) -> Unit,
    onSignOut: () -> Unit
) {
    val elections by viewModel.elections.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UniVote") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Welcome, ${user.displayName}", style = MaterialTheme.typography.headlineSmall)
                    Text("Elections", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (elections.isEmpty()) item { Text("There are no elections available yet.") }
            items(elections, key = { it.election.id }) { item -> ElectionCard(item, onVote) }
        }
    }
}

@Composable
private fun ElectionCard(item: VoterElectionItem, onVote: (Long) -> Unit) {
    val e = item.election
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleLarge)
            if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${DateFormats.format(e.startTime)} to ${DateFormats.format(e.endTime)}",
                style = MaterialTheme.typography.bodySmall
            )
            val status = when (e.status) {
                ElectionStatus.OPEN -> "Voting is open"
                ElectionStatus.SUSPENDED -> "Voting is temporarily suspended"
                ElectionStatus.SCHEDULED -> "Voting opens on ${DateFormats.format(e.startTime)}"
                ElectionStatus.CLOSED -> "Voting has closed"
                ElectionStatus.DRAFT -> ""
            }
            Text(status, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            if (item.receiptCode != null) {
                Text("You have voted. Receipt code: ${item.receiptCode}", style = MaterialTheme.typography.bodyMedium)
            } else if (e.status == ElectionStatus.OPEN) {
                Button(onClick = { onVote(e.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Vote now")
                }
            }
        }
    }
}