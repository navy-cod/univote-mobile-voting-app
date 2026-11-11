package com.univote.evoting.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.univote.evoting.data.repository.SessionUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    user: SessionUser,
    onSignOut: () -> Unit,
    onElections: () -> Unit,
    onVoters: () -> Unit,
    onDepartments: () -> Unit,
    onParties: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UniVote Admin") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Welcome, ${user.displayName}", style = MaterialTheme.typography.headlineSmall)
            ListCard("Elections", "Create elections, positions and candidates; open and close voting", onClick = onElections)
            ListCard("Voters", "Manage the voter roll and eligibility; import from CSV", onClick = onVoters)
            ListCard("Departments", "Add and edit university departments", onClick = onDepartments)
            ListCard("Political parties", "Add and edit parties", onClick = onParties)
        }
    }
}