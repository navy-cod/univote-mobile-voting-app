package com.univote.evoting.ui.support

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
import com.univote.evoting.data.local.TicketRow
import com.univote.evoting.data.repository.SupportRepository
import com.univote.evoting.domain.label
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.ListCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SupportMineViewModel(repo: SupportRepository) : ViewModel() {
    val tickets: StateFlow<List<TicketRow>> =
        repo.observeMine().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SupportMineScreen(viewModel: SupportMineViewModel, onBack: () -> Unit, onNew: () -> Unit) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()

    AdminScaffold(
        title = "My reports",
        onBack = onBack,
        message = null,
        onMessageShown = {},
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onNew) { Text("New report") } }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tickets.isEmpty()) item { Text("You have not sent any reports. Tap \"New report\" to start.") }
            items(tickets, key = { it.id }) { t ->
                ListCard(
                    title = "${t.referenceNo} | ${t.status.label()}",
                    subtitle = buildString {
                        append(t.type.label()).append(": ").append(t.subject)
                        append("\nSent ").append(DateFormats.format(t.createdAt))
                        t.resolutionNote?.let { append("\nOutcome: ").append(it) }
                    }
                )
            }
        }
    }
}