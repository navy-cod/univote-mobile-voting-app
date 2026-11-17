package com.univote.evoting.ui.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.repository.ActionResult
import com.univote.evoting.data.repository.FeedbackRepository
import com.univote.evoting.ui.admin.AdminScaffold
import com.univote.evoting.ui.admin.DropdownField
import com.univote.evoting.ui.admin.LabeledTextField
import com.univote.evoting.ui.auth.ErrorText
import kotlinx.coroutines.launch

data class FeedbackState(
    val rating: Int? = null,
    val message: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val sent: Boolean = false
)

class FeedbackViewModel(private val repo: FeedbackRepository) : ViewModel() {
    var state by mutableStateOf(FeedbackState())
        private set

    fun onRating(v: Int) { state = state.copy(rating = v, error = null) }
    fun onMessage(v: String) { state = state.copy(message = v, error = null) }

    fun submit() {
        val s = state
        if (s.isSubmitting) return
        state = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            state = when (val r = repo.submit(s.rating, s.message)) {
                ActionResult.Success -> FeedbackState(sent = true)
                is ActionResult.Failure -> s.copy(isSubmitting = false, error = r.message)
            }
        }
    }
}

@Composable
fun FeedbackScreen(viewModel: FeedbackViewModel, onBack: () -> Unit) {
    val state = viewModel.state
    AdminScaffold(title = "Send feedback", onBack = onBack, message = null, onMessageShown = {}) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.sent) {
                Text("Thank you for your feedback", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            } else {
                Text("Tell us how the app worked for you.", style = MaterialTheme.typography.bodyMedium)
                DropdownField(
                    label = "Rating",
                    options = listOf(
                        5 to "5 - Excellent", 4 to "4 - Good", 3 to "3 - Okay", 2 to "2 - Poor", 1 to "1 - Very poor"
                    ),
                    selected = state.rating,
                    onSelected = viewModel::onRating
                )
                LabeledTextField(
                    state.message, viewModel::onMessage, "Your feedback",
                    singleLine = false, enabled = !state.isSubmitting
                )
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (state.isSubmitting) "Sending..." else "Send feedback") }
            }
        }
    }
}