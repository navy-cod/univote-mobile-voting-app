package com.univote.evoting.data.repository

sealed interface ActionResult {
    data object Success : ActionResult
    data class Failure(val message: String) : ActionResult
}