package com.univote.evoting.ui.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.NotificationType

/**
 * In-app notification inbox.
 * [onSimulate] is non-null only in debug builds and adds "simulate" entries to the overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onSimulate: ((SimulatedEvent) -> Unit)? = null
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val unreadCount = items?.count { !it.isRead } ?: 0
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::markAllRead, enabled = unreadCount > 0) {
                        Text("Mark all read")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear all") },
                                enabled = !items.isNullOrEmpty(),
                                onClick = { menuOpen = false; confirmClear = true }
                            )
                            if (onSimulate != null) {
                                SimulatedEvent.entries.forEach { event ->
                                    DropdownMenuItem(
                                        text = { Text(event.label) },
                                        onClick = { menuOpen = false; onSimulate(event) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SystemNotificationsOffBanner()

            when (val list = items) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> if (list.isEmpty()) EmptyInbox() else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { item ->
                            NotificationRow(
                                item = item,
                                onClick = { viewModel.open(item) },
                                onDelete = { viewModel.delete(item) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all notifications?") },
            text = { Text("This removes every notification from your inbox. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearAll() }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NotificationRow(item: NotificationEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val unread = !item.isRead
    val relativeTime = remember(item.createdAt) {
        DateUtils.getRelativeTimeSpanString(
            item.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (unread) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = item.type.icon(),
            contentDescription = null, // decorative: the title carries the meaning
            tint = item.type.tint()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                // Screen readers say "Unread" / "Read" so state is not conveyed by colour alone.
                .semantics(mergeDescendants = true) { stateDescription = if (unread) "Unread" else "Read" },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
            )
            Text(text = item.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = relativeTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete notification: ${item.title}")
        }
    }
}

@Composable
private fun EmptyInbox() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Notifications, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("No notifications yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Vote confirmations, alerts and published results will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The inbox works regardless, but tell the user when status-bar alerts are blocked and how to fix it. */
@Composable
private fun SystemNotificationsOffBanner() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    // Re-check when returning from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    if (enabled) return

    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Phone alerts are turned off, so you may miss results or security alerts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { openNotificationSettings(context) }) { Text("Turn on") }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.VOTE_CONFIRMATION, NotificationType.CONFIRMATION -> Icons.Default.CheckCircle
    NotificationType.ERROR -> Icons.Default.Warning
    NotificationType.RESULTS -> Icons.Default.Star
    NotificationType.SCHEDULE -> Icons.Default.DateRange
    NotificationType.SUPPORT -> Icons.Default.Build
    NotificationType.INFO -> Icons.Default.Info
}

@Composable
private fun NotificationType.tint(): Color = when (this) {
    NotificationType.ERROR -> MaterialTheme.colorScheme.error
    NotificationType.VOTE_CONFIRMATION, NotificationType.CONFIRMATION, NotificationType.RESULTS ->
        MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
