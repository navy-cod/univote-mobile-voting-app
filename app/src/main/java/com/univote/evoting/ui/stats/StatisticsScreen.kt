package com.univote.evoting.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.repository.ElectionStats
import com.univote.evoting.data.repository.StatisticsRepository
import com.univote.evoting.ui.admin.AdminScaffold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(electionId: Long, repo: StatisticsRepository) : ViewModel() {
    val stats: StateFlow<ElectionStats?> =
        repo.observe(electionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onBack: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    AdminScaffold(title = "Live statistics", onBack = onBack, message = null, onMessageShown = {}) { padding ->
        val s = stats
        if (s == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.electionTitle, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Status: ${s.status} | ends ${DateFormats.format(s.endTime)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("Updates automatically as votes are cast.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Overall turnout", style = MaterialTheme.typography.titleMedium)
                            ProgressRow("Voters who have voted", s.overallVoted, s.overallEligible)
                        }
                    }
                }
                item { Text("By position", style = MaterialTheme.typography.titleMedium) }
                items(s.positions) { ProgressRow(it.title, it.voted, it.eligible) }
                item { Text("By department", style = MaterialTheme.typography.titleMedium) }
                items(s.departments) { ProgressRow(it.name, it.voted, it.eligible) }
                item {
                    Text(
                        "Candidate totals stay hidden until the official results are published.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String, voted: Int, eligible: Int) {
    val fraction = if (eligible > 0) (voted.toFloat() / eligible).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$voted of $eligible (${"%.0f".format(fraction * 100)}%)", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}