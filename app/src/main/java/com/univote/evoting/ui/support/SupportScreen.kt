package com.univote.evoting.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.univote.evoting.data.local.entity.SupportTicketEntity
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.local.entity.TicketType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(viewModel: SupportViewModel, onBack: () -> Unit) {
    val tickets by viewModel.tickets.collectAsState()
    var type by remember { mutableStateOf(TicketType.TECHNICAL) }
    var typeMenu by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var submittedReference by remember { mutableStateOf<String?>(null) }
    var trackingReference by remember { mutableStateOf("") }
    var trackedTicket by remember { mutableStateOf<SupportTicketEntity?>(null) }
    var trackingMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Support & fraud reporting") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Report a problem", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Submit a technical issue or report suspicious activity. Keep your reference number to track progress.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("New report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Report type", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (type == TicketType.TECHNICAL) "Technical support" else "Fraud / suspicious activity")
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            DropdownMenuItem(text = { Text("Technical support") }, onClick = { type = TicketType.TECHNICAL; typeMenu = false })
                            DropdownMenuItem(text = { Text("Fraud / suspicious activity") }, onClick = { type = TicketType.FRAUD; typeMenu = false })
                        }
                        OutlinedTextField(value = subject, onValueChange = { subject = it; validation = null }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = description, onValueChange = { description = it; validation = null }, label = { Text("Describe the issue") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(
                            onClick = {
                                if (subject.isBlank() || description.isBlank()) validation = "Enter a subject and describe the issue."
                                else if (subject.length > 100 || description.length > 2000) validation = "Subject must be under 100 characters and description under 2,000 characters."
                                else scope.launch {
                                    submittedReference = viewModel.submit(type, subject, description)
                                    subject = ""; description = ""
                                }
                            }, modifier = Modifier.fillMaxWidth()
                        ) { Text("Submit report") }
                        submittedReference?.let { ref ->
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("Report submitted", fontWeight = FontWeight.Bold)
                                    Text("Your reference number: $ref", style = MaterialTheme.typography.titleMedium)
                                    Text("Save this number to follow up on your report.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Track a report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = trackingReference, onValueChange = { trackingReference = it.uppercase(Locale.ROOT); trackingMessage = null }, label = { Text("Reference number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = {
                            trackedTicket = tickets.firstOrNull { it.referenceNo.equals(trackingReference.trim(), true) }
                            trackingMessage = if (trackedTicket == null) "No matching report found in your available reports. Check the reference number or contact support." else null
                        }, modifier = Modifier.fillMaxWidth()) { Text("Check status") }
                        trackingMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        trackedTicket?.let { TicketCard(it) }
                    }
                }
            }
            item {
                Text(if (viewModel.canManageReports) "All submitted reports" else "Your reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (tickets.isEmpty()) {
                item { Text("No reports have been submitted yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(tickets, key = { it.id }) { ticket ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TicketCard(ticket)
                        if (viewModel.canManageReports && ticket.status != TicketStatus.RESOLVED && ticket.status != TicketStatus.CLOSED) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.updateStatus(ticket, TicketStatus.IN_REVIEW) }) { Text("Mark in review") }
                                Button(onClick = { viewModel.updateStatus(ticket, TicketStatus.RESOLVED, "Reviewed by support team") }) { Text("Resolve") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun TicketCard(ticket: SupportTicketEntity) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ticket.referenceNo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                StatusBadge(ticket.status)
            }
            Text(ticket.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (ticket.type == TicketType.TECHNICAL) "Technical support" else "Fraud / suspicious activity", style = MaterialTheme.typography.labelMedium)
            Text(ticket.description, style = MaterialTheme.typography.bodyMedium)
            Text("Submitted ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ticket.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ticket.resolutionNote?.let { Text("Support note: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StatusBadge(status: TicketStatus) {
    val label = when (status) {
        TicketStatus.OPEN -> "Open"
        TicketStatus.IN_REVIEW -> "In Review"
        TicketStatus.RESOLVED -> "Resolved"
        TicketStatus.CLOSED -> "Closed"
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
