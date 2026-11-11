package com.univote.evoting.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.repository.ElectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ElectionsViewModel(private val repo: ElectionRepository) : AdminViewModel() {

    val elections: StateFlow<List<ElectionEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.closeExpired() }
    }

    fun create(title: String, description: String, start: Long, end: Long, onSuccess: () -> Unit) =
        perform("Election created as a draft.", onSuccess) { repo.create(title, description, start, end) }
}

@Composable
fun ElectionsScreen(viewModel: ElectionsViewModel, onBack: () -> Unit, onOpenElection: (Long) -> Unit) {
    val elections by viewModel.elections.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }

    AdminScaffold(
        title = "Elections",
        onBack = onBack,
        message = if (showForm) null else viewModel.message,
        onMessageShown = viewModel::clearMessage,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { viewModel.clearMessage(); showForm = true }) {
                Text("New election")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (elections.isEmpty()) item { Text("No elections yet. Tap \"New election\".") }
            items(elections, key = { it.id }) { e ->
                ListCard(
                    title = e.title,
                    subtitle = "${e.status} | ${DateFormats.format(e.startTime)} to ${DateFormats.format(e.endTime)}",
                    onClick = { onOpenElection(e.id) }
                )
            }
        }
    }

    if (showForm) {
        ElectionFormDialog(
            initial = null,
            error = viewModel.message,
            onDismiss = { showForm = false },
            onSave = { title, description, start, end ->
                viewModel.create(title, description, start, end) { showForm = false }
            }
        )
    }
}

private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

/** Used for both creating and editing an election. Dates are typed as yyyy-MM-dd HH:mm. */
@Composable
fun ElectionFormDialog(
    initial: ElectionEntity?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, start: Long, end: Long) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var start by remember { mutableStateOf(DateFormats.format(initial?.startTime ?: now)) }
    var end by remember { mutableStateOf(DateFormats.format(initial?.endTime ?: (now + SEVEN_DAYS_MS))) }
    var localError by remember { mutableStateOf<String?>(null) }

    FormDialog(
        title = if (initial == null) "New election" else "Edit election",
        error = localError ?: error,
        onDismiss = onDismiss,
        onConfirm = {
            val s = DateFormats.parse(start)
            val e = DateFormats.parse(end)
            if (s == null || e == null) {
                localError = "Use the format yyyy-MM-dd HH:mm (for example 2026-12-01 08:00)."
            } else {
                localError = null
                onSave(title, description, s, e)
            }
        }
    ) {
        LabeledTextField(title, { title = it }, "Title")
        LabeledTextField(description, { description = it }, "Description", singleLine = false)
        LabeledTextField(start, { start = it }, "Voting starts (yyyy-MM-dd HH:mm)")
        LabeledTextField(end, { end = it }, "Voting ends (yyyy-MM-dd HH:mm)")
    }
}