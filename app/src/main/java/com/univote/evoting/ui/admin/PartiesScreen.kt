package com.univote.evoting.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.repository.PartyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PartiesViewModel(private val repo: PartyRepository) : AdminViewModel() {

    val parties: StateFlow<List<PoliticalPartyEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, acronym: String, slogan: String, onSuccess: () -> Unit) =
        perform(if (id == null) "Party added." else "Party updated.", onSuccess) {
            if (id == null) repo.add(name, acronym, slogan) else repo.update(id, name, acronym, slogan)
        }

    fun delete(party: PoliticalPartyEntity) = perform("Party deleted.") { repo.delete(party) }
}

@Composable
fun PartiesScreen(viewModel: PartiesViewModel, onBack: () -> Unit) {
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PoliticalPartyEntity?>(null) }
    var deleting by remember { mutableStateOf<PoliticalPartyEntity?>(null) }

    AdminScaffold(
        title = "Political parties",
        onBack = onBack,
        message = if (showForm) null else viewModel.message,
        onMessageShown = viewModel::clearMessage,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                viewModel.clearMessage(); editing = null; showForm = true
            }) { Text("Add party") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(parties, key = { it.id }) { p ->
                ListCard(
                    title = "${p.name} (${p.acronym})",
                    subtitle = p.slogan?.let { "\"$it\"" }
                ) {
                    TextButton(onClick = { viewModel.clearMessage(); editing = p; showForm = true }) { Text("Edit") }
                    TextButton(onClick = { deleting = p }) { Text("Delete") }
                }
            }
        }
    }

    if (showForm) {
        PartyFormDialog(
            initial = editing,
            error = viewModel.message,
            onDismiss = { showForm = false },
            onSave = { name, acronym, slogan ->
                viewModel.save(editing?.id, name, acronym, slogan) { showForm = false }
            }
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = "Delete party?",
            text = "Delete ${p.name}? This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(p); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun PartyFormDialog(
    initial: PoliticalPartyEntity?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var acronym by remember { mutableStateOf(initial?.acronym.orEmpty()) }
    var slogan by remember { mutableStateOf(initial?.slogan.orEmpty()) }
    FormDialog(
        title = if (initial == null) "Add party" else "Edit party",
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSave(name, acronym, slogan) }
    ) {
        LabeledTextField(name, { name = it }, "Party name")
        LabeledTextField(acronym, { acronym = it }, "Acronym (e.g. PSF)", capitalization = KeyboardCapitalization.Characters)
        LabeledTextField(slogan, { slogan = it }, "Slogan (optional)")
    }
}