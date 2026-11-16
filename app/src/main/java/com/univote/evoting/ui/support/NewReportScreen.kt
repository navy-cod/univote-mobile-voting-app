package com.univote.evoting.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.local.entity.TicketType
import com.univote.evoting.data.repository.SubmitResult
import com.univote.evoting.data.repository.SupportRepository
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.DropdownField
import com.univote.evoting.ui.admin.LabeledTextField
import com.univote.evoting.ui.auth.ErrorText
import kotlinx.coroutines.launch

data class NewReportState(
    val type: TicketType = TicketType.TECHNICAL,
    val subject: String = "",
    val description: String = "",
    val evidenceUri: String? = null,
    val evidenceName: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val reference: String? = null
)

class NewReportViewModel(private val repo: SupportRepository) : ViewModel() {

    var state by mutableStateOf(NewReportState())
        private set

    fun onType(v: TicketType) { state = state.copy(type = v, error = null) }
    fun onSubject(v: String) { state = state.copy(subject = v, error = null) }
    fun onDescription(v: String) { state = state.copy(description = v, error = null) }
    fun attach(uri: String, name: String) { state = state.copy(evidenceUri = uri, evidenceName = name) }
    fun removeEvidence() { state = state.copy(evidenceUri = null, evidenceName = null) }

    fun submit() {
        val s = state
        if (s.isSubmitting) return
        state = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            state = when (val r = repo.submit(s.type, s.subject, s.description, s.evidenceUri)) {
                is SubmitResult.Success -> NewReportState(reference = r.referenceNo)
                is SubmitResult.Failure -> s.copy(isSubmitting = false, error = r.message)
            }
        }
    }
}

@Composable
fun NewReportScreen(viewModel: NewReportViewModel, onBack: () -> Unit) {
    val state = viewModel.state
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Not every provider allows persistent access; the attachment may not open later.
            }
            viewModel.attach(uri.toString(), displayName(context, uri))
        }
    }

    AdminScaffold(title = "New report", onBack = onBack, message = null, onMessageShown = {}) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.reference != null) {
                Text("Report submitted", style = MaterialTheme.typography.headlineSmall)
                Card {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Reference number", style = MaterialTheme.typography.labelLarge)
                        Text(state.reference, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    }
                }
                Text("Keep this number. You can follow the status under My reports and you will get an alert when it changes.")
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            } else {
                DropdownField(
                    label = "Type",
                    options = listOf(
                        TicketType.TECHNICAL to "Technical problem",
                        TicketType.FRAUD to "Suspected fraud or cheating"
                    ),
                    selected = state.type,
                    onSelected = viewModel::onType
                )
                LabeledTextField(state.subject, viewModel::onSubject, "Subject", enabled = !state.isSubmitting)
                LabeledTextField(
                    state.description, viewModel::onDescription, "What happened?",
                    singleLine = false, enabled = !state.isSubmitting
                )
                if (state.evidenceName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Attached: ${state.evidenceName}", modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::removeEvidence) { Text("Remove") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Attach evidence (optional)") }
                }
                Text(
                    "Never include your password or how you voted in a report.",
                    style = MaterialTheme.typography.bodySmall
                )
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (state.isSubmitting) "Sending..." else "Submit report") }
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String =
    (try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }) ?: "attachment"