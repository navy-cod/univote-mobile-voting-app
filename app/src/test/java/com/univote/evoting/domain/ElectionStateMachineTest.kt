package com.univote.evoting.domain

import com.univote.evoting.data.local.entity.ElectionStatus.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectionStateMachineTest {
    @Test fun happyPath_isAllowed() {
        assertTrue(ElectionStateMachine.canTransition(DRAFT, SCHEDULED))
        assertTrue(ElectionStateMachine.canTransition(SCHEDULED, OPEN))
        assertTrue(ElectionStateMachine.canTransition(OPEN, SUSPENDED))
        assertTrue(ElectionStateMachine.canTransition(SUSPENDED, OPEN))
        assertTrue(ElectionStateMachine.canTransition(OPEN, CLOSED))
    }

    @Test fun skippingSteps_isRejected() {
        assertFalse(ElectionStateMachine.canTransition(DRAFT, OPEN))
        assertFalse(ElectionStateMachine.canTransition(SCHEDULED, CLOSED))
    }

    @Test fun closed_isFinal() {
        listOf(DRAFT, SCHEDULED, OPEN, SUSPENDED, CLOSED).forEach {
            assertFalse(ElectionStateMachine.canTransition(CLOSED, it))
        }
    }

    @Test fun onlyDraftAndScheduled_areEditable() {
        assertTrue(ElectionStateMachine.isEditable(DRAFT))
        assertTrue(ElectionStateMachine.isEditable(SCHEDULED))
        assertFalse(ElectionStateMachine.isEditable(OPEN))
        assertFalse(ElectionStateMachine.isEditable(SUSPENDED))
        assertFalse(ElectionStateMachine.isEditable(CLOSED))
    }
}