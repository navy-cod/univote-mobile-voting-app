package com.univote.evoting.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.repository.ActionResult
import kotlinx.coroutines.launch

/** Shared plumbing: run a repository action, show its result message, and run [onSuccess] if it worked. */
abstract class AdminViewModel : ViewModel() {

    var message by mutableStateOf<String?>(null)
        protected set

    fun clearMessage() {
        message = null
    }

    protected fun perform(
        successText: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> ActionResult
    ) {
        viewModelScope.launch {
            when (val result = block()) {
                ActionResult.Success -> {
                    message = successText
                    onSuccess()
                }
                is ActionResult.Failure -> {
                    message = result.message
                }
            }
        }
    }
}