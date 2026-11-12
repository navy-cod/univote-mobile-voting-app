package com.univote.evoting.ui.support

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.local.TicketRow
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.repository.SupportRepository
import com.univote.evoting.domain.TicketWorkflow
import com.univote.evoting.domain.label
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.AdminViewModel
import com.univote.evoting.ui.admin.LabeledTextField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TicketDetailViewModel(
    private val ticketId: Long,
    private val repo: SupportRepository
) : AdminViewModel() {

    val ticket: StateFlow<TicketRow?> =
        repo.observeTicket(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun assignToMe() = perform("Assigned to you and moved to review.") { repo.assignToMe(ticketId) }

    fun changeStatus(target: TicketStatus, note: String, onSuccess: () -> Unit) =
        perform("Report updated.", onSuccess) { repo.changeStatus(ticketId, target, note) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketDetailScreen(viewModel: TicketDetailViewModel, onBack: () -> Unit) {
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf("") }
    val context = LocalContext.current

    AdminScaffold(
        title = ticket?.referenceNo ?: "Report",
        onBack = onBack,
        message = viewModel.message,
        onMessageShown = viewModel::clearMessage
    ) { padding ->
        val t = ticket
        if (t == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t.subject, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${t.type.label()} | ${t.status.label()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Reported by ${t.reporterName} on ${DateFormats.format(t.createdAt)}", style = MaterialTheme.typography.bodySmall)
                Text("Assigned to: ${t.assignedToName ?: "nobody yet"}", style = MaterialTheme.typography.bodySmall)
                Card(Modifier.fillMaxWidth()) { Text(t.description, Modifier.padding(16.dp)) }

                t.evidenceUri?.let { uri ->
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setData(Uri.parse(uri))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "The attachment can no longer be opened.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("View attached evidence") }
                }
                t.resolutionNote?.let {
                    Text("Outcome note: $it", style = MaterialTheme.typography.bodyMedium)
                }

                if (t.status != TicketStatus.CLOSED) {
                    HorizontalDivider()
                    if (t.status == TicketStatus.OPEN || t.status == TicketStatus.IN_REVIEW) {
                        Button(
                            onClick = viewModel::assignToMe,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(if (t.status == TicketStatus.OPEN) "Take this report (start review)" else "Assign to me") }
                    }
                    LabeledTextField(
                        note, { note = it }, "Note for the reporter (required to resolve or close)",
                        singleLine = false
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TicketWorkflow.nextOptions(t.status)
                            .filter { !(t.status == TicketStatus.OPEN && it == TicketStatus.IN_REVIEW) }
                            .forEach { target ->
                                val label = when (target) {
                                    TicketStatus.IN_REVIEW -> "Reopen"
                                    TicketStatus.RESOLVED -> "Mark resolved"
                                    TicketStatus.CLOSED -> "Close report"
                                    TicketStatus.OPEN -> "Open"
                                }
                                OutlinedButton(
                                    onClick = { viewModel.changeStatus(target, note) { note = "" } },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text(label) }
                            }
                    }
                } else {
                    Text("This report is closed and can no longer be changed.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}