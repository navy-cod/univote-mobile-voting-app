package com.univote.evoting.ui.audit

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.local.entity.AuditLogEntity
import com.univote.evoting.data.repository.AuditLoad
import com.univote.evoting.data.repository.AuditRepository
import com.univote.evoting.domain.ChainStatus
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.ListCard
import kotlinx.coroutines.launch

sealed interface AuditUiState {
    data object Loading : AuditUiState
    data class Denied(val message: String) : AuditUiState
    data class Ready(val entries: List<AuditLogEntity>, val total: Int, val chain: ChainStatus) : AuditUiState
}

class AuditLogViewModel(private val repo: AuditRepository) : ViewModel() {

    var state by mutableStateOf<AuditUiState>(AuditUiState.Loading)
        private set

    /** Set when an export is ready to be shared; the screen clears it after handing it to Android. */
    var exportText by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            state = when (val r = repo.load()) {
                is AuditLoad.Ready -> AuditUiState.Ready(r.entries, r.total, r.chain)
                is AuditLoad.Denied -> AuditUiState.Denied(r.message)
            }
        }
    }

    fun export() {
        viewModelScope.launch { exportText = repo.exportCsv() }
    }

    fun exportHandled() {
        exportText = null
    }
}

@Composable
fun AuditLogScreen(viewModel: AuditLogViewModel, onBack: () -> Unit) {
    val state = viewModel.state
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.exportText) {
        viewModel.exportText?.let { csv ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "UniVote audit records")
                putExtra(Intent.EXTRA_TEXT, csv)
            }
            context.startActivity(Intent.createChooser(send, "Export audit records"))
            viewModel.exportHandled()
        }
    }

    AdminScaffold(
        title = "Audit records",
        onBack = onBack,
        message = null,
        onMessageShown = {},
        actions = {
            if (state is AuditUiState.Ready) TextButton(onClick = viewModel::export) { Text("Export") }
        }
    ) { padding ->
        when (state) {
            AuditUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is AuditUiState.Denied -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
            is AuditUiState.Ready -> {
                val shown = remember(state.entries, query) {
                    val q = query.trim()
                    if (q.isEmpty()) state.entries else state.entries.filter {
                        it.actor.contains(q, true) || it.action.contains(q, true) || it.details.contains(q, true)
                    }
                }
                LazyColumn(
                    Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { ChainBanner(state.chain, state.total, onVerify = viewModel::refresh) }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search actor, action or details") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            "Showing ${shown.size} of the latest ${state.entries.size} entries (${state.total} in total). Read-only.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    items(shown, key = { it.id }) { e ->
                        ListCard(
                            title = "#${e.id}  ${e.action}",
                            subtitle = "${DateFormats.format(e.timestamp)} | ${e.actor}" +
                                    if (e.details.isNotBlank()) "\n${e.details}" else ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainBanner(chain: ChainStatus, total: Int, onVerify: () -> Unit) {
    val broken = chain is ChainStatus.Broken
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (broken) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (chain) {
                is ChainStatus.Intact -> {
                    Text("Record chain verified", style = MaterialTheme.typography.titleMedium)
                    Text("All $total entries are linked and unchanged.", style = MaterialTheme.typography.bodyMedium)
                }
                is ChainStatus.Broken -> {
                    Text("WARNING: record chain is broken", style = MaterialTheme.typography.titleMedium)
                    Text("Problem found at entry #${chain.entryId}. ${chain.reason}", style = MaterialTheme.typography.bodyMedium)
                    Text("Report this to the system administrator.", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onVerify, modifier = Modifier.heightIn(min = 48.dp)) { Text("Verify again") }
        }
    }
}