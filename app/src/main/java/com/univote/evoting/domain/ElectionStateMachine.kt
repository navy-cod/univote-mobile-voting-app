package com.univote.evoting.domain

import com.univote.evoting.data.local.entity.ElectionStatus

/** The only legal election life-cycle: DRAFT -> SCHEDULED -> OPEN <-> SUSPENDED -> CLOSED. */
object ElectionStateMachine {
    private val allowed: Map<ElectionStatus, Set<ElectionStatus>> = mapOf(
        ElectionStatus.DRAFT to setOf(ElectionStatus.SCHEDULED),
        ElectionStatus.SCHEDULED to setOf(ElectionStatus.OPEN),
        ElectionStatus.OPEN to setOf(ElectionStatus.SUSPENDED, ElectionStatus.CLOSED),
        ElectionStatus.SUSPENDED to setOf(ElectionStatus.OPEN, ElectionStatus.CLOSED),
        ElectionStatus.CLOSED to emptySet()
    )

    fun canTransition(from: ElectionStatus, to: ElectionStatus): Boolean =
        to in allowed.getValue(from)

    /** Setup (details, positions, candidates) may only change before voting starts. */
    fun isEditable(status: ElectionStatus): Boolean =
        status == ElectionStatus.DRAFT || status == ElectionStatus.SCHEDULED
}