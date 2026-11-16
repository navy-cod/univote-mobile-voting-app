package com.univote.evoting.ui.support

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.repository.NotificationRepository
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.ui.admin.ListCard
import com.univote.evoting.ui.notifications.AlertsButton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class UnreadViewModel(repo: NotificationRepository) : ViewModel() {
    val unread: StateFlow<Int> =
        repo.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** Home for Support Officers and System Administrators. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHomeScreen(
    user: SessionUser,
    viewModel: UnreadViewModel,
    canAudit: Boolean,
    onQueue: () -> Unit,
    onAudit: () -> Unit,
    onAlerts: () -> Unit,
    onSignOut: () -> Unit
) {
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UniVote Staff") },
                actions = {
                    AlertsButton(unread, onAlerts)
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Welcome, ${user.displayName}", style = MaterialTheme.typography.headlineSmall)
            ListCard("Support and fraud reports", "Review, assign and resolve reports from users", onClick = onQueue)
            if (canAudit) ListCard("Audit records", "Read-only history of important activity", onClick = onAudit)
        }
    }
}