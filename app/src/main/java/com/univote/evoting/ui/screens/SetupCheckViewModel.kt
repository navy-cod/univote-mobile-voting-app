package com.univote.evoting.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univote.evoting.data.local.UniVoteDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TableCount(val label: String, val count: Int)

/** Phase 1 verification only: proves the database was created and seeded. Removed in Phase 2. */
class SetupCheckViewModel(db: UniVoteDatabase) : ViewModel() {

    private val labels = listOf(
        "Departments", "Voters on roll", "User accounts", "Political parties",
        "Elections", "Positions", "Candidates"
    )

    val counts: StateFlow<List<TableCount>> = combine(
        listOf(
            db.departmentDao().observeCount(),
            db.voterDao().observeCount(),
            db.userDao().observeCount(),
            db.partyDao().observeCount(),
            db.electionDao().observeCount(),
            db.positionDao().observeCount(),
            db.candidateDao().observeCount()
        )
    ) { values ->
        labels.zip(values.toList()).map { (label, count) -> TableCount(label, count) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
