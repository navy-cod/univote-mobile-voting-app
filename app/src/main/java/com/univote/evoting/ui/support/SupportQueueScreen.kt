package com.univote.evoting.ui.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.local.TicketRow
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.repository.SupportRepository
import com.univote.evoting.domain.label
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.ListCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SupportQueueViewModel(repo: SupportRepository) : ViewModel() {
    val tickets: StateFlow<List<TicketRow>> =
        repo.observeQueue().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var filter by mutableStateOf<TicketStatus?>(null)
        private set

    fun setFilter(status: TicketStatus?) {
        filter = status
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SupportQueueScreen(viewModel: SupportQueueViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val filter = viewModel.filter
    val shown = remember(tickets, filter) { if (filter == null) tickets else tickets.filter { it.status == filter } }

    AdminScaffold(title = "Support and fraud reports", onBack = onBack, message = null, onMessageShown = {}) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text("All (${tickets.size})") }
                    )
                    TicketStatus.entries.forEach { status ->
                        FilterChip(
                            selected = filter == status,
                            onClick = { viewModel.setFilter(status) },
                            label = { Text("${status.label()} (${tickets.count { it.status == status }})") }
                        )
                    }
                }
            }
            if (shown.isEmpty()) item { Text("No reports here.") }
            items(shown, key = { it.id }) { t ->
                ListCard(
                    title = "${t.referenceNo} | ${t.status.label()}",
                    subtitle = "${t.type.label()}: ${t.subject}\nFrom ${t.reporterName} on ${DateFormats.format(t.createdAt)}",
                    onClick = { onOpen(t.id) }
                )
            }
        }
    }
}