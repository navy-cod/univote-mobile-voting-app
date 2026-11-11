package com.univote.evoting.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.repository.AccountResult
import com.univote.evoting.data.repository.AuthRepository
import kotlinx.coroutines.launch

data class RegisterUiState(
    val regNumber: String = "",
    val fullName: String = "",
    val password: String = "",
    val confirm: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val created: Boolean = false
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {

    var uiState by mutableStateOf(RegisterUiState())
        private set

    fun onRegNumberChange(v: String) { uiState = uiState.copy(regNumber = v, error = null) }
    fun onFullNameChange(v: String) { uiState = uiState.copy(fullName = v, error = null) }
    fun onPasswordChange(v: String) { uiState = uiState.copy(password = v, error = null) }
    fun onConfirmChange(v: String) { uiState = uiState.copy(confirm = v, error = null) }

    fun register() {
        val s = uiState
        if (s.isLoading) return
        if (s.regNumber.isBlank() || s.fullName.isBlank() || s.password.isEmpty()) {
            uiState = s.copy(error = "Fill in all fields.")
            return
        }
        if (s.password != s.confirm) {
            uiState = s.copy(error = "Passwords do not match.")
            return
        }
        uiState = s.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val chars = s.password.toCharArray()
            val result = try {
                auth.registerVoter(s.regNumber, s.fullName, chars)
            } finally {
                chars.fill('\u0000')
            }
            uiState = when (result) {
                AccountResult.Success -> RegisterUiState(created = true)
                is AccountResult.Failure ->
                    uiState.copy(isLoading = false, password = "", confirm = "", error = result.message)
            }
        }
    }
}