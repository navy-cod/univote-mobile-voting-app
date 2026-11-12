package com.univote.evoting.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.NotificationType
import com.univote.evoting.data.repository.NotificationPublisher
import com.univote.evoting.data.repository.NotificationRepository
import com.univote.evoting.data.repository.voteConfirmed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Debug-build only: lets you see each kind of notification before voting/results screens exist. */
enum class SimulatedEvent(val label: String) {
    VOTE_CONFIRMED("Simulate: vote confirmation"),
    ERROR("Simulate: error"),
    RESULTS_PUBLISHED("Simulate: results published")
}

class NotificationsViewModel(
    private val userId: Long,
    private val repository: NotificationRepository,
    private val publisher: NotificationPublisher
) : ViewModel() {

    /** null while the first load is in flight, so the screen can tell "loading" from "empty". */
    val items: StateFlow<List<NotificationEntity>?> = repository.observeInbox(userId)
        .stateIn<List<NotificationEntity>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(item: NotificationEntity) {
        if (item.isRead) return
        viewModelScope.launch { repository.markRead(userId, item.id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead(userId) }
    }

    fun delete(item: NotificationEntity) {
        viewModelScope.launch { repository.delete(userId, item.id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll(userId) }
    }

    fun simulate(event: SimulatedEvent) {
        viewModelScope.launch {
            when (event) {
                SimulatedEvent.VOTE_CONFIRMED ->
                    publisher.voteConfirmed(userId, "Student Council Elections 2026", "UV-DEMO-1234")
                SimulatedEvent.ERROR -> publisher.publish(
                    userId, NotificationType.ERROR, "Vote not recorded",
                    "We could not record your vote because the election has closed."
                )
                SimulatedEvent.RESULTS_PUBLISHED -> publisher.publish(
                    userId, NotificationType.RESULTS, "Results published",
                    "Official results for \"Student Council Elections 2026\" are now available."
                )
            }
        }
    }
}
