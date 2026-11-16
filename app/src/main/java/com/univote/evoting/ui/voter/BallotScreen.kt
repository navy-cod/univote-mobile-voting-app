package com.univote.evoting.ui.voter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.repository.BallotPosition
import com.univote.evoting.data.repository.VoteReceipt
import com.univote.evoting.ui.auth.ErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallotScreen(viewModel: BallotViewModel, onBack: () -> Unit) {
    val state = viewModel.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ballot") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(if (state is BallotUiState.Voting) "Cancel" else "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BallotUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is BallotUiState.Unavailable ->
                    MessagePanel("Voting unavailable", state.message, onBack)
                is BallotUiState.AlreadyVoted ->
                    ReceiptPanel("You have already voted", state.receipt, onBack)
                is BallotUiState.Done ->
                    ReceiptPanel("Your vote was recorded", state.receipt, onBack)
                is BallotUiState.Voting ->
                    if (state.reviewing) ReviewPanel(state, viewModel) else SelectPanel(state, viewModel)
            }
        }
    }
}

@Composable
private fun SelectPanel(state: BallotUiState.Voting, viewModel: BallotViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.form.electionTitle, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Choose one candidate for each position, or abstain. You will review your ballot before submitting. " +
                            "A submitted vote is final.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(state.form.positions, key = { it.positionId }) { position ->
            PositionChoiceCard(
                position = position,
                decided = state.selections.containsKey(position.positionId),
                selected = state.selections[position.positionId],
                onSelect = { viewModel.select(position.positionId, it) }
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { ErrorText(it) }
                if (!state.allDecided) {
                    Text("Choose an option for every position to continue.", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = viewModel::review,
                    enabled = state.allDecided,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Review ballot") }
            }
        }
    }
}

@Composable
private fun PositionChoiceCard(
    position: BallotPosition,
    decided: Boolean,
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(position.title, style = MaterialTheme.typography.titleMedium)
            position.candidates.forEach { c ->
                ChoiceRow(
                    selected = decided && selected == c.id,
                    label = c.name,
                    supporting = "${c.party}\n${c.manifesto}",
                    onClick = { onSelect(c.id) }
                )
            }
            ChoiceRow(
                selected = decided && selected == null,
                label = "Abstain (no vote for this position)",
                supporting = null,
                onClick = { onSelect(null) }
            )
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, supporting: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ReviewPanel(state: BallotUiState.Voting, viewModel: BallotViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Review your ballot", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Check your choices. After you submit, your vote cannot be changed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(state.form.positions, key = { it.positionId }) { position ->
            val choiceId = state.selections[position.positionId]
            val choice = position.candidates.firstOrNull { it.id == choiceId }
            ListItemCard(
                title = position.title,
                text = choice?.let { "${it.name} (${it.party})" } ?: "Abstain"
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (state.isSubmitting) "Submitting..." else "Submit my vote (final)") }
                OutlinedButton(
                    onClick = viewModel::backToEdit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Go back and change") }
            }
        }
    }
}

@Composable
private fun ListItemCard(title: String, text: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ReceiptPanel(title: String, receipt: VoteReceipt, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(receipt.electionTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Card {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Receipt code", style = MaterialTheme.typography.labelLarge)
                Text(receipt.code, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            "Recorded ${DateFormats.format(receipt.castAt)} for ${receipt.positionCount} position(s).",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Keep this code. It proves your vote was recorded, but it does not reveal how you voted.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
    }
}

@Composable
private fun MessagePanel(title: String, message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back") }
    }
}