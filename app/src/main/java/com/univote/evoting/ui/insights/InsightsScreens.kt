package com.univote.evoting.ui.insights

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
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.repository.ElectionRepository
import com.univote.evoting.data.repository.NotificationRepository
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.notifications.AlertsButton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class InsightsViewModel(
    electionRepo: ElectionRepository,
    notificationRepo: NotificationRepository
) : ViewModel() {

    val elections: StateFlow<List<ElectionEntity>> = electionRepo.observeAll()
        .map { list -> list.filter { it.status != ElectionStatus.DRAFT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unread: StateFlow<Int> =
        notificationRepo.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@Composable
fun AdminInsightsScreen(
    viewModel: InsightsViewModel,
    onBack: () -> Unit,
    onStats: (Long) -> Unit,
    onResults: (Long) -> Unit
) {
    val elections by viewModel.elections.collectAsStateWithLifecycle()
    AdminScaffold(title = "Results and statistics", onBack = onBack, message = null, onMessageShown = {}) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (elections.isEmpty()) item { Text("No published elections yet.") }
            items(elections, key = { it.id }) { e ->
                InsightCard(e, isAdmin = true, onStats = { onStats(e.id) }, onResults = { onResults(e.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserverHomeScreen(
    user: SessionUser,
    viewModel: InsightsViewModel,
    onStats: (Long) -> Unit,
    onResults: (Long) -> Unit,
    onAudit: () -> Unit,
    onSupport: () -> Unit,
    onAlerts: () -> Unit,
    onSignOut: () -> Unit
) {
    val elections by viewModel.elections.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UniVote Observer") },
                actions = {
                    AlertsButton(unread, onAlerts)
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Welcome, ${user.displayName}", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = onAudit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Audit records")
                    }
                    OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Report a problem")
                    }
                }
            }
            if (elections.isEmpty()) item { Text("No published elections yet.") }
            items(elections, key = { it.id }) { e ->
                InsightCard(e, isAdmin = false, onStats = { onStats(e.id) }, onResults = { onResults(e.id) })
            }
        }
    }
}

@Composable
private fun InsightCard(e: ElectionEntity, isAdmin: Boolean, onStats: () -> Unit, onResults: () -> Unit) {
    val (resultsLabel, resultsEnabled) = when {
        e.resultsPublished -> "View official results" to true
        isAdmin && e.status == ElectionStatus.CLOSED -> "Count and publish results" to true
        else -> "Results not published" to false
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "${e.status} | ${DateFormats.format(e.startTime)} to ${DateFormats.format(e.endTime)}",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onStats, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Live statistics")
            }
            Button(
                onClick = onResults,
                enabled = resultsEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(resultsLabel) }
        }
    }
}