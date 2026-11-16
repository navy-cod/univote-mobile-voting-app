package com.univote.evoting.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.CandidateStatus
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.repository.DepartmentRepository
import com.univote.evoting.data.repository.ElectionRepository
import com.univote.evoting.data.repository.PartyRepository
import com.univote.evoting.domain.ElectionStateMachine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CandidateItem(val candidate: CandidateEntity, val partyLabel: String)
data class PositionSection(
    val position: PositionEntity,
    val departmentLabel: String,
    val candidates: List<CandidateItem>
)

class ElectionDetailViewModel(
    private val electionId: Long,
    private val repo: ElectionRepository,
    partyRepo: PartyRepository,
    deptRepo: DepartmentRepository
) : AdminViewModel() {

    private val sharing = SharingStarted.WhileSubscribed(5_000)

    val election: StateFlow<ElectionEntity?> =
        repo.observe(electionId).stateIn(viewModelScope, sharing, null)

    val parties: StateFlow<List<PoliticalPartyEntity>> =
        partyRepo.observeAll().stateIn(viewModelScope, sharing, emptyList())

    val departments: StateFlow<List<DepartmentEntity>> =
        deptRepo.observeAll().stateIn(viewModelScope, sharing, emptyList())

    val sections: StateFlow<List<PositionSection>> = combine(
        repo.observePositions(electionId),
        repo.observeCandidates(electionId),
        partyRepo.observeAll(),
        deptRepo.observeAll()
    ) { positionList, candidateList, partyList, deptList ->
        val partyNames = partyList.associate { it.id to it.acronym }
        val deptNames = deptList.associate { it.id to it.name }
        positionList.map { p ->
            PositionSection(
                position = p,
                departmentLabel = p.departmentId?.let { deptNames[it] } ?: "All departments",
                candidates = candidateList.filter { it.positionId == p.id }.map { c ->
                    CandidateItem(c, c.partyId?.let { partyNames[it] } ?: "Independent")
                }
            )
        }
    }.stateIn(viewModelScope, sharing, emptyList())

    init {
        viewModelScope.launch { repo.closeExpired() }
    }

    fun changeStatus(target: ElectionStatus, label: String) =
        perform("Done: $label.") { repo.changeStatus(electionId, target) }

    fun updateDetails(title: String, description: String, start: Long, end: Long, onSuccess: () -> Unit) =
        perform("Election updated.", onSuccess) { repo.update(electionId, title, description, start, end) }

    fun addPosition(title: String, departmentId: Long?, onSuccess: () -> Unit) =
        perform("Position added.", onSuccess) { repo.addPosition(electionId, title, departmentId) }

    fun deletePosition(position: PositionEntity) =
        perform("Position removed.") { repo.deletePosition(position.id) }

    fun addCandidate(positionId: Long, partyId: Long?, name: String, manifesto: String, onSuccess: () -> Unit) =
        perform("Candidate added (pending approval).", onSuccess) {
            repo.addCandidate(positionId, partyId, name, manifesto)
        }

    fun updateCandidate(candidateId: Long, partyId: Long?, name: String, manifesto: String, onSuccess: () -> Unit) =
        perform("Candidate updated and set back to pending.", onSuccess) {
            repo.updateCandidate(candidateId, partyId, name, manifesto)
        }

    fun setCandidateStatus(candidate: CandidateEntity, status: CandidateStatus) =
        perform("${candidate.fullName}: ${status.name.lowercase()}.") {
            repo.setCandidateStatus(candidate.id, status)
        }

    fun deleteCandidate(candidate: CandidateEntity) =
        perform("Candidate removed.") { repo.deleteCandidate(candidate.id) }
}

private sealed interface DetailDialog {
    data object EditElection : DetailDialog
    data object AddPosition : DetailDialog
    data class AddCandidate(val position: PositionEntity) : DetailDialog
    data class EditCandidate(val candidate: CandidateEntity) : DetailDialog
    data class ConfirmStatus(val target: ElectionStatus, val label: String) : DetailDialog
    data class ConfirmDeletePosition(val position: PositionEntity) : DetailDialog
    data class ConfirmDeleteCandidate(val candidate: CandidateEntity) : DetailDialog
}

private fun DetailDialog?.isForm() =
    this is DetailDialog.EditElection || this is DetailDialog.AddPosition ||
            this is DetailDialog.AddCandidate || this is DetailDialog.EditCandidate

private fun statusActions(status: ElectionStatus): List<Pair<ElectionStatus, String>> = when (status) {
    ElectionStatus.DRAFT -> listOf(ElectionStatus.SCHEDULED to "Publish (schedule)")
    ElectionStatus.SCHEDULED -> listOf(ElectionStatus.OPEN to "Open voting")
    ElectionStatus.OPEN -> listOf(
        ElectionStatus.SUSPENDED to "Suspend voting",
        ElectionStatus.CLOSED to "Close voting"
    )
    ElectionStatus.SUSPENDED -> listOf(
        ElectionStatus.OPEN to "Resume voting",
        ElectionStatus.CLOSED to "Close voting"
    )
    ElectionStatus.CLOSED -> emptyList()
}

@Composable
fun ElectionDetailScreen(viewModel: ElectionDetailViewModel, onBack: () -> Unit) {
    val election by viewModel.election.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val departments by viewModel.departments.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }
    val current = dialog

    fun open(d: DetailDialog) {
        viewModel.clearMessage()
        dialog = d
    }

    AdminScaffold(
        title = election?.title ?: "Election",
        onBack = onBack,
        message = if (current.isForm()) null else viewModel.message,
        onMessageShown = viewModel::clearMessage
    ) { padding ->
        val e = election
        if (e == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val editable = ElectionStateMachine.isEditable(e.status)
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ElectionHeader(
                        e = e,
                        editable = editable,
                        onEdit = { open(DetailDialog.EditElection) },
                        onStatus = { target, label -> dialog = DetailDialog.ConfirmStatus(target, label) }
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Positions and candidates", style = MaterialTheme.typography.titleLarge)
                        if (editable) Button(onClick = { open(DetailDialog.AddPosition) }) { Text("Add position") }
                    }
                }
                if (sections.isEmpty()) item { Text("No positions yet.") }
                items(sections, key = { it.position.id }) { section ->
                    PositionCard(
                        section = section,
                        editable = editable,
                        onAddCandidate = { open(DetailDialog.AddCandidate(section.position)) },
                        onDeletePosition = { dialog = DetailDialog.ConfirmDeletePosition(section.position) },
                        onEditCandidate = { open(DetailDialog.EditCandidate(it)) },
                        onSetStatus = viewModel::setCandidateStatus,
                        onDeleteCandidate = { dialog = DetailDialog.ConfirmDeleteCandidate(it) }
                    )
                }
            }
        }
    }

    when (current) {
        null -> Unit
        DetailDialog.EditElection -> election?.let { e ->
            ElectionFormDialog(
                initial = e,
                error = viewModel.message,
                onDismiss = { dialog = null },
                onSave = { t, d, s, en -> viewModel.updateDetails(t, d, s, en) { dialog = null } }
            )
        }
        DetailDialog.AddPosition -> PositionFormDialog(
            departments = departments,
            error = viewModel.message,
            onDismiss = { dialog = null },
            onSave = { title, deptId -> viewModel.addPosition(title, deptId) { dialog = null } }
        )
        is DetailDialog.AddCandidate -> CandidateFormDialog(
            initial = null,
            parties = parties,
            error = viewModel.message,
            onDismiss = { dialog = null },
            onSave = { partyId, name, manifesto ->
                viewModel.addCandidate(current.position.id, partyId, name, manifesto) { dialog = null }
            }
        )
        is DetailDialog.EditCandidate -> CandidateFormDialog(
            initial = current.candidate,
            parties = parties,
            error = viewModel.message,
            onDismiss = { dialog = null },
            onSave = { partyId, name, manifesto ->
                viewModel.updateCandidate(current.candidate.id, partyId, name, manifesto) { dialog = null }
            }
        )
        is DetailDialog.ConfirmStatus -> ConfirmDialog(
            title = current.label + "?",
            text = when (current.target) {
                ElectionStatus.OPEN -> "Voters will be able to vote while the election is open. Setup becomes locked."
                ElectionStatus.CLOSED -> "Closing ends voting permanently. This cannot be undone."
                ElectionStatus.SUSPENDED -> "Voting pauses until you resume it."
                else -> "Continue?"
            },
            confirmLabel = "Confirm",
            onConfirm = { viewModel.changeStatus(current.target, current.label); dialog = null },
            onDismiss = { dialog = null }
        )
        is DetailDialog.ConfirmDeletePosition -> ConfirmDialog(
            title = "Remove position?",
            text = "Remove ${current.position.title} and all of its candidates?",
            confirmLabel = "Remove",
            onConfirm = { viewModel.deletePosition(current.position); dialog = null },
            onDismiss = { dialog = null }
        )
        is DetailDialog.ConfirmDeleteCandidate -> ConfirmDialog(
            title = "Remove candidate?",
            text = "Remove ${current.candidate.fullName}?",
            confirmLabel = "Remove",
            onConfirm = { viewModel.deleteCandidate(current.candidate); dialog = null },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun ElectionHeader(
    e: ElectionEntity,
    editable: Boolean,
    onEdit: () -> Unit,
    onStatus: (ElectionStatus, String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.title, style = MaterialTheme.typography.headlineSmall)
            if (e.description.isNotBlank()) Text(e.description)
            Text("Status: ${e.status}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Voting period: ${DateFormats.format(e.startTime)} to ${DateFormats.format(e.endTime)}")
            if (editable) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Edit details")
                }
            } else {
                Text("Setup is locked once voting has started.", style = MaterialTheme.typography.bodySmall)
            }
            statusActions(e.status).forEach { (target, label) ->
                Button(
                    onClick = { onStatus(target, label) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(label) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionCard(
    section: PositionSection,
    editable: Boolean,
    onAddCandidate: () -> Unit,
    onDeletePosition: () -> Unit,
    onEditCandidate: (CandidateEntity) -> Unit,
    onSetStatus: (CandidateEntity, CandidateStatus) -> Unit,
    onDeleteCandidate: (CandidateEntity) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.position.title, style = MaterialTheme.typography.titleMedium)
            Text("Open to: ${section.departmentLabel}", style = MaterialTheme.typography.bodySmall)
            if (section.candidates.isEmpty()) Text("No candidates yet.")
            section.candidates.forEach { item ->
                val c = item.candidate
                HorizontalDivider()
                Text("${c.fullName} (${item.partyLabel})", style = MaterialTheme.typography.titleSmall)
                Text("Status: ${c.status}", style = MaterialTheme.typography.bodySmall)
                Text(c.manifesto, style = MaterialTheme.typography.bodyMedium)
                if (editable) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onEditCandidate(c) }) { Text("Edit") }
                        if (c.status != CandidateStatus.APPROVED) {
                            TextButton(onClick = { onSetStatus(c, CandidateStatus.APPROVED) }) { Text("Approve") }
                        }
                        if (c.status != CandidateStatus.REJECTED) {
                            TextButton(onClick = { onSetStatus(c, CandidateStatus.REJECTED) }) { Text("Reject") }
                        }
                        TextButton(onClick = { onDeleteCandidate(c) }) { Text("Remove") }
                    }
                }
            }
            if (editable) {
                HorizontalDivider()
                Row {
                    TextButton(onClick = onAddCandidate) { Text("Add candidate") }
                    TextButton(onClick = onDeletePosition) { Text("Remove position") }
                }
            }
        }
    }
}

@Composable
private fun PositionFormDialog(
    departments: List<DepartmentEntity>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var deptId by remember { mutableStateOf<Long?>(null) }
    val options: List<Pair<Long?, String>> =
        listOf(Pair<Long?, String>(null, "All departments")) +
                departments.map { Pair<Long?, String>(it.id, it.name) }
    FormDialog(
        title = "Add position",
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSave(title, deptId) }
    ) {
        LabeledTextField(title, { title = it }, "Position title")
        DropdownField(label = "Open to", options = options, selected = deptId, onSelected = { deptId = it })
    }
}

@Composable
private fun CandidateFormDialog(
    initial: CandidateEntity?,
    parties: List<PoliticalPartyEntity>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.fullName.orEmpty()) }
    var manifesto by remember { mutableStateOf(initial?.manifesto.orEmpty()) }
    var partyId by remember { mutableStateOf(initial?.partyId) }
    val options: List<Pair<Long?, String>> =
        listOf(Pair<Long?, String>(null, "Independent")) +
                parties.map { Pair<Long?, String>(it.id, "${it.name} (${it.acronym})") }
    FormDialog(
        title = if (initial == null) "Add candidate" else "Edit candidate",
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSave(partyId, name, manifesto) }
    ) {
        LabeledTextField(name, { name = it }, "Candidate name")
        DropdownField(label = "Party", options = options, selected = partyId, onSelected = { partyId = it })
        LabeledTextField(manifesto, { manifesto = it }, "Manifesto", singleLine = false)
        if (initial != null) {
            Text("Saving changes sets the candidate back to pending approval.", style = MaterialTheme.typography.bodySmall)
        }
    }
}