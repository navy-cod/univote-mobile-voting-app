package com.univote.evoting.domain

import com.univote.evoting.data.local.entity.TicketStatus.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketWorkflowTest {
    @Test fun normalPath_isAllowed() {
        assertTrue(TicketWorkflow.canTransition(OPEN, IN_REVIEW))
        assertTrue(TicketWorkflow.canTransition(IN_REVIEW, RESOLVED))
        assertTrue(TicketWorkflow.canTransition(RESOLVED, CLOSED))
        assertTrue(TicketWorkflow.canTransition(RESOLVED, IN_REVIEW)) // reopen
    }

    @Test fun skippingReview_isRejected() {
        assertFalse(TicketWorkflow.canTransition(OPEN, RESOLVED))
    }

    @Test fun closed_isFinal() {
        listOf(OPEN, IN_REVIEW, RESOLVED, CLOSED).forEach { assertFalse(TicketWorkflow.canTransition(CLOSED, it)) }
    }

    @Test fun onlyOutcomesNeedANote() {
        assertTrue(TicketWorkflow.requiresNote(RESOLVED))
        assertTrue(TicketWorkflow.requiresNote(CLOSED))
        assertFalse(TicketWorkflow.requiresNote(IN_REVIEW))
    }
}