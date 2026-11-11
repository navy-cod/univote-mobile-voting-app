package com.univote.evoting.ui.admin

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.VoterEntity
import com.univote.evoting.data.repository.DepartmentRepository
import com.univote.evoting.data.repository.ImportSummary
import com.univote.evoting.data.repository.VoterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoterItem(val voter: VoterEntity, val departmentName: String)

class VotersViewModel(
    private val voterRepo: VoterRepository,
    deptRepo: DepartmentRepository
) : AdminViewModel() {

    val items: StateFlow<List<VoterItem>> = combine(voterRepo.observeAll(), deptRepo.observeAll()) { voters, depts ->
        val names = depts.associate { it.id to it.name }
        voters.map { VoterItem(it, names[it.departmentId] ?: "Unknown") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val departments: StateFlow<List<DepartmentEntity>> =
        deptRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var importSummary by mutableStateOf<ImportSummary?>(null)
        private set
    var isImporting by mutableStateOf(false)
        private set

    fun save(
        existing: VoterEntity?, regNumber: String, fullName: String,
        departmentId: Long?, year: String, eligible: Boolean, onSuccess: () -> Unit
    ) = perform(if (existing == null) "Voter added." else "Voter updated.", onSuccess) {
        if (existing == null) {
            voterRepo.add(regNumber, fullName, departmentId, year.toIntOrNull(), eligible)
        } else {
            voterRepo.update(existing.regNumber, fullName, departmentId, year.toIntOrNull(), eligible)
        }
    }

    fun setEligible(voter: VoterEntity, eligible: Boolean) =
        perform(if (eligible) "Marked eligible." else "Marked not eligible.") {
            voterRepo.setEligible(voter.regNumber, eligible)
        }

    fun importFrom(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            isImporting = true
            val text = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text == null) {
                message = "Could not read that file."
            } else if (text.length > 1_000_000) {
                message = "The file is too large (limit 1 MB)."
            } else {
                importSummary = voterRepo.importCsv(text)
            }
            isImporting = false
        }
    }

    fun dismissSummary() {
        importSummary = null
    }
}

@Composable
fun VotersScreen(viewModel: VotersViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val departments by viewModel.departments.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VoterEntity?>(null) }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(context.contentResolver, uri)
    }

    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items else items.filter {
            it.voter.fullName.contains(q, ignoreCase = true) ||
                    it.voter.regNumber.contains(q, ignoreCase = true) ||
                    it.departmentName.contains(q, ignoreCase = true)
        }
    }

    AdminScaffold(
        title = "Voters",
        onBack = onBack,
        message = if (showForm) null else viewModel.message,
        onMessageShown = viewModel::clearMessage,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                viewModel.clearMessage(); editing = null; showForm = true
            }) { Text("Add voter") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search name, number or department") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !viewModel.isImporting,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(if (viewModel.isImporting) "Importing..." else "Import CSV") }
            }
            Text(
                "${filtered.size} of ${items.size} voters",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.voter.regNumber }) { item ->
                    val v = item.voter
                    ListCard(
                        title = v.fullName,
                        subtitle = "${v.regNumber} | ${item.departmentName} | Year ${v.yearOfStudy}"
                    ) {
                        Text(
                            if (v.isEligible) "Eligible" else "Not eligible",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Switch(
                            checked = v.isEligible,
                            onCheckedChange = { viewModel.setEligible(v, it) },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TextButton(onClick = { viewModel.clearMessage(); editing = v; showForm = true }) { Text("Edit") }
                    }
                }
            }
        }
    }

    if (showForm) {
        VoterFormDialog(
            initial = editing,
            departments = departments,
            error = viewModel.message,
            onDismiss = { showForm = false },
            onSave = { reg, name, deptId, year, eligible ->
                viewModel.save(editing, reg, name, deptId, year, eligible) { showForm = false }
            }
        )
    }

    viewModel.importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSummary,
            title = { Text("Import finished") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Added: ${summary.added}")
                    Text("Skipped (already on roll): ${summary.skipped}")
                    Text("Rows with problems: ${summary.errors.size}")
                    summary.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissSummary) { Text("OK") } }
        )
    }
}

@Composable
private fun VoterFormDialog(
    initial: VoterEntity?,
    departments: List<DepartmentEntity>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, String, Boolean) -> Unit
) {
    var reg by remember { mutableStateOf(initial?.regNumber.orEmpty()) }
    var name by remember { mutableStateOf(initial?.fullName.orEmpty()) }
    var deptId by remember { mutableStateOf(initial?.departmentId) }
    var year by remember { mutableStateOf(initial?.yearOfStudy?.toString().orEmpty()) }
    var eligible by remember { mutableStateOf(initial?.isEligible ?: true) }

    FormDialog(
        title = if (initial == null) "Add voter" else "Edit voter",
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSave(reg, name, deptId, year, eligible) }
    ) {
        LabeledTextField(
            reg, { reg = it }, "Registration number",
            enabled = initial == null, capitalization = KeyboardCapitalization.Characters
        )
        LabeledTextField(name, { name = it }, "Full name", capitalization = KeyboardCapitalization.Words)
        DropdownField(
            label = "Department",
            options = departments.map { Pair<Long?, String>(it.id, it.name) },
            selected = deptId,
            onSelected = { deptId = it }
        )
        LabeledTextField(year, { year = it.filter(Char::isDigit).take(1) }, "Year of study (1-6)", keyboardType = KeyboardType.Number)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Eligible to vote", modifier = Modifier.weight(1f))
            Switch(checked = eligible, onCheckedChange = { eligible = it })
        }
    }
}