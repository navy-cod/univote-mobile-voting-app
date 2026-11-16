package com.univote.evoting.domain

import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.local.entity.TicketType

object TicketWorkflow {
    private val allowed: Map<TicketStatus, Set<TicketStatus>> = mapOf(
        TicketStatus.OPEN to setOf(TicketStatus.IN_REVIEW, TicketStatus.CLOSED),
        TicketStatus.IN_REVIEW to setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED),
        TicketStatus.RESOLVED to setOf(TicketStatus.CLOSED, TicketStatus.IN_REVIEW),
        TicketStatus.CLOSED to emptySet()
    )

    fun canTransition(from: TicketStatus, to: TicketStatus) = to in allowed.getValue(from)
    fun nextOptions(from: TicketStatus): Set<TicketStatus> = allowed.getValue(from)

    /** Outcomes must be explained so the reporter knows what happened. */
    fun requiresNote(target: TicketStatus) = target == TicketStatus.RESOLVED || target == TicketStatus.CLOSED
}

fun TicketStatus.label() = when (this) {
    TicketStatus.OPEN -> "Open"
    TicketStatus.IN_REVIEW -> "In review"
    TicketStatus.RESOLVED -> "Resolved"
    TicketStatus.CLOSED -> "Closed"
}

fun TicketType.label() = when (this) {
    TicketType.TECHNICAL -> "Technical problem"
    TicketType.FRAUD -> "Suspected fraud"
}