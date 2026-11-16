package com.univote.evoting.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.local.entity.SupportTicketEntity
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.local.entity.TicketType
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.data.repository.SupportTicketRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportViewModel(
    private val repository: SupportTicketRepository,
    private val user: SessionUser
) : ViewModel() {
    val canManageReports: Boolean = user.role in setOf(
        Role.SUPPORT_OFFICER, Role.SYSTEM_ADMIN, Role.ELECTION_ADMIN
    )

    val tickets: StateFlow<List<SupportTicketEntity>> =
        (if (canManageReports) repository.observeAll() else repository.observeForUser(user.id))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun submit(type: TicketType, subject: String, description: String): String =
        repository.submit(user.id, type, subject, description)

    fun updateStatus(ticket: SupportTicketEntity, status: TicketStatus, note: String? = null) {
        viewModelScope.launch { repository.updateStatus(ticket.id, status, note) }
    }
}
