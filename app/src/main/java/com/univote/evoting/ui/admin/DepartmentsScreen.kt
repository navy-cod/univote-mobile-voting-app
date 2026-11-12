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
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.repository.DepartmentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DepartmentsViewModel(private val repo: DepartmentRepository) : AdminViewModel() {

    val departments: StateFlow<List<DepartmentEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, code: String, onSuccess: () -> Unit) =
        perform(if (id == null) "Department added." else "Department updated.", onSuccess) {
            if (id == null) repo.add(name, code) else repo.update(id, name, code)
        }

    fun delete(department: DepartmentEntity) =
        perform("Department deleted.") { repo.delete(department) }
}

@Composable
fun DepartmentsScreen(viewModel: DepartmentsViewModel, onBack: () -> Unit) {
    val departments by viewModel.departments.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DepartmentEntity?>(null) }
    var deleting by remember { mutableStateOf<DepartmentEntity?>(null) }

    AdminScaffold(
        title = "Departments",
        onBack = onBack,
        message = if (showForm) null else viewModel.message,
        onMessageShown = viewModel::clearMessage,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                viewModel.clearMessage(); editing = null; showForm = true
            }) { Text("Add department") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(departments, key = { it.id }) { d ->
                ListCard(title = d.name, subtitle = "Code: ${d.code}") {
                    TextButton(onClick = { viewModel.clearMessage(); editing = d; showForm = true }) { Text("Edit") }
                    TextButton(onClick = { deleting = d }) { Text("Delete") }
                }
            }
        }
    }

    if (showForm) {
        DepartmentFormDialog(
            initial = editing,
            error = viewModel.message,
            onDismiss = { showForm = false },
            onSave = { name, code -> viewModel.save(editing?.id, name, code) { showForm = false } }
        )
    }
    deleting?.let { d ->
        ConfirmDialog(
            title = "Delete department?",
            text = "Delete ${d.name}? This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(d); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun DepartmentFormDialog(
    initial: DepartmentEntity?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    FormDialog(
        title = if (initial == null) "Add department" else "Edit department",
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSave(name, code) }
    ) {
        LabeledTextField(name, { name = it }, "Department name")
        LabeledTextField(code, { code = it }, "Short code (e.g. CS)", capitalization = KeyboardCapitalization.Characters)
    }
}