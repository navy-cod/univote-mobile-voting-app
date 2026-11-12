package com.univote.evoting.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.repository.NotificationRepository
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.ListCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repo: NotificationRepository) : ViewModel() {

    val items: StateFlow<List<NotificationEntity>> =
        repo.observeMine().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markRead(id: Long) {
        viewModelScope.launch { repo.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repo.markAllRead() }
    }
}

@Composable
fun AlertsButton(unreadCount: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(if (unreadCount > 0) "Alerts ($unreadCount)" else "Alerts") }
}

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    AdminScaffold(
        title = "Alerts",
        onBack = onBack,
        message = null,
        onMessageShown = {},
        actions = { TextButton(onClick = viewModel::markAllRead) { Text("Mark all read") } }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (items.isEmpty()) item { Text("You have no alerts yet.") }
            items(items, key = { it.id }) { n ->
                ListCard(
                    title = (if (n.isRead) "" else "NEW: ") + n.title,
                    subtitle = "${n.message}\n${DateFormats.format(n.createdAt)}",
                    onClick = { viewModel.markRead(n.id) }
                )
            }
        }
    }
}