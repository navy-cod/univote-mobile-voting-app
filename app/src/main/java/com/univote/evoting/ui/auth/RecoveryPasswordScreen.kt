package com.univote.evoting.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.univote.evoting.core.security.PasswordPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoverPasswordScreen(viewModel: RecoverPasswordViewModel, onBack: () -> Unit) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reset password") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        AuthColumn(modifier = Modifier.padding(padding)) {
            if (state.done) {
                Text("Password updated", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Go to sign in")
                }
            } else {
                Text(
                    "Verify your identity with details from the student roll, then choose a new password.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = state.regNumber,
                    onValueChange = viewModel::onRegNumberChange,
                    label = { Text("Registration number") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.fullName,
                    onValueChange = viewModel::onFullNameChange,
                    label = { Text("Full name") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.year,
                    onValueChange = viewModel::onYearChange,
                    label = { Text("Year of study (1-6)") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                PasswordField(state.password, viewModel::onPasswordChange, "New password", enabled = !state.isLoading)
                PasswordField(
                    value = state.confirm,
                    onValueChange = viewModel::onConfirmChange,
                    label = "Confirm new password",
                    enabled = !state.isLoading,
                    imeAction = ImeAction.Done,
                    onDone = viewModel::recover
                )
                Text(PasswordPolicy.HINT, style = MaterialTheme.typography.bodySmall)
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::recover,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (state.isLoading) "Verifying..." else "Reset password") }
            }
        }
    }
}