package com.univote.evoting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsCalculatorTest {

    private fun raw(vararg votes: Int) =
        votes.mapIndexed { i, v -> RawCandidate(i + 1L, "Candidate ${'A' + i}", "Party", v) }

    @Test
    fun clearLeader_isTheOnlyWinner() {
        val r = ResultsCalculator.rank(raw(3, 7, 1))
        assertEquals("Candidate B", r.candidates.first().name)
        assertTrue(r.candidates.first().isWinner)
        assertEquals(1, r.candidates.count { it.isWinner })
        assertFalse(r.isTie)
    }

    @Test
    fun tieAtTheTop_declaresNoWinner() {
        val r = ResultsCalculator.rank(raw(5, 5, 2))
        assertTrue(r.isTie)
        assertTrue(r.candidates.none { it.isWinner })
    }

    @Test
    fun tieBelowTheTop_isNotATie() {
        val r = ResultsCalculator.rank(raw(9, 2, 2))
        assertFalse(r.isTie)
        assertTrue(r.candidates.first().isWinner)
    }

    @Test
    fun noVotes_meansNoWinnerAndNoTie() {
        val r = ResultsCalculator.rank(raw(0, 0))
        assertFalse(r.isTie)
        assertTrue(r.candidates.none { it.isWinner })
        assertTrue(r.candidates.all { it.percent == 0.0 })
    }

    @Test
    fun percentagesUseValidVotesAndSumToHundred() {
        val r = ResultsCalculator.rank(raw(1, 1, 2))
        assertEquals(50.0, r.candidates.first().percent, 0.001)
        assertEquals(100.0, r.candidates.sumOf { it.percent }, 0.001)
    }

    @Test
    fun orderedByVotesThenName() {
        val r = ResultsCalculator.rank(raw(2, 4, 2))
        assertEquals(listOf("Candidate B", "Candidate A", "Candidate C"), r.candidates.map { it.name })
    }

    @Test
    fun emptyList_isHandled() {
        val r = ResultsCalculator.rank(emptyList())
        assertTrue(r.candidates.isEmpty())
        assertFalse(r.isTie)
    }
}