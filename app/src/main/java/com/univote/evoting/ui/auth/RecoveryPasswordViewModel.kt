package com.univote.evoting.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.repository.AccountResult
import com.univote.evoting.data.repository.AuthRepository
import kotlinx.coroutines.launch

data class RecoverUiState(
    val regNumber: String = "",
    val fullName: String = "",
    val year: String = "",
    val password: String = "",
    val confirm: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

class RecoverPasswordViewModel(private val auth: AuthRepository) : ViewModel() {

    var uiState by mutableStateOf(RecoverUiState())
        private set

    fun onRegNumberChange(v: String) { uiState = uiState.copy(regNumber = v, error = null) }
    fun onFullNameChange(v: String) { uiState = uiState.copy(fullName = v, error = null) }
    fun onYearChange(v: String) { uiState = uiState.copy(year = v.filter(Char::isDigit).take(1), error = null) }
    fun onPasswordChange(v: String) { uiState = uiState.copy(password = v, error = null) }
    fun onConfirmChange(v: String) { uiState = uiState.copy(confirm = v, error = null) }

    fun recover() {
        val s = uiState
        if (s.isLoading) return
        val year = s.year.toIntOrNull()
        if (s.regNumber.isBlank() || s.fullName.isBlank() || year == null || s.password.isEmpty()) {
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
                auth.recoverPassword(s.regNumber, s.fullName, year, chars)
            } finally {
                chars.fill('\u0000')
            }
            uiState = when (result) {
                AccountResult.Success -> RecoverUiState(done = true)
                is AccountResult.Failure ->
                    uiState.copy(isLoading = false, password = "", confirm = "", error = result.message)
            }
        }
    }
}