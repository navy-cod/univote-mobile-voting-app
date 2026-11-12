package com.univote.evoting.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.repository.SessionUser

/** Temporary role-specific landing page. Later phases replace it with the real dashboards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePlaceholderScreen(
    user: SessionUser,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UniVote") },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge { Text(if (unreadNotifications > 99) "99+" else unreadNotifications.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = if (unreadNotifications > 0) "Notifications, $unreadNotifications unread" else "Notifications"
                            )
                        }
                    }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Welcome, ${user.displayName}", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Signed in as: ${user.role.label()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Coming in later phases:", style = MaterialTheme.typography.titleSmall)
            user.role.upcomingFeatures().forEach { Text("\u2022 $it") }
        }
    }
}

private fun Role.label() = when (this) {
    Role.VOTER -> "Voter"
    Role.ELECTION_ADMIN -> "Election Administrator"
    Role.SYSTEM_ADMIN -> "System Administrator"
    Role.OBSERVER -> "Election Observer"
    Role.SUPPORT_OFFICER -> "Technical Support Officer"
}

private fun Role.upcomingFeatures(): List<String> = when (this) {
    Role.VOTER -> listOf("Active elections and candidates", "Cast your vote", "Live turnout and published results", "Report a problem or suspected fraud")
    Role.ELECTION_ADMIN -> listOf("Manage voters and departments", "Manage candidates and parties", "Open and close elections", "Approve and publish results")
    Role.SYSTEM_ADMIN -> listOf("Manage accounts and roles", "System configuration")
    Role.OBSERVER -> listOf("Live participation statistics", "Read-only audit records")
    Role.SUPPORT_OFFICER -> listOf("Review and assign support and fraud reports")
}