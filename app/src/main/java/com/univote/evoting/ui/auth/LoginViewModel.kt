package com.univote.evoting.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.core.session.SessionManager
import com.univote.evoting.data.repository.AuthRepository
import com.univote.evoting.data.repository.LoginResult
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class LoginViewModel(
    private val auth: AuthRepository,
    private val session: SessionManager
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onUsernameChange(value: String) { uiState = uiState.copy(username = value, error = null) }
    fun onPasswordChange(value: String) { uiState = uiState.copy(password = value, error = null) }

    fun login(onSuccess: () -> Unit) {
        val s = uiState
        if (s.isLoading) return
        if (s.username.isBlank() || s.password.isEmpty()) {
            uiState = s.copy(error = "Enter your username or registration number and your password.")
            return
        }
        uiState = s.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val chars = s.password.toCharArray()
            val result = try {
                auth.login(s.username, chars)
            } finally {
                chars.fill('\u0000') // wipe the password copy as soon as we are done
            }
            when (result) {
                is LoginResult.Success -> {
                    session.start(result.user)
                    uiState = LoginUiState()
                    onSuccess()
                }
                LoginResult.InvalidCredentials ->
                    uiState = uiState.copy(isLoading = false, password = "", error = "Invalid username or password.")
                is LoginResult.Locked ->
                    uiState = uiState.copy(
                        isLoading = false, password = "",
                        error = "Too many failed attempts. Try again in ${result.minutesLeft} minute(s)."
                    )
                LoginResult.AccountDisabled ->
                    uiState = uiState.copy(isLoading = false, password = "", error = "This account is disabled. Contact support.")
            }
        }
    }
}