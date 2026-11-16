package com.univote.evoting.data.repository

import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.ElectionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class PositionStats(val title: String, val eligible: Int, val voted: Int)
data class DepartmentStats(val name: String, val eligible: Int, val voted: Int)

/** Participation only. There are deliberately no candidate totals anywhere in this model. */
data class ElectionStats(
    val electionTitle: String,
    val status: ElectionStatus,
    val endTime: Long,
    val overallEligible: Int,
    val overallVoted: Int,
    val positions: List<PositionStats>,
    val departments: List<DepartmentStats>
)

class StatisticsRepository(db: UniVoteDatabase) {
    private val electionDao = db.electionDao()
    private val positionDao = db.positionDao()
    private val participationDao = db.participationDao()
    private val voterDao = db.voterDao()
    private val departmentDao = db.departmentDao()

    /** Re-emits automatically whenever a vote, voter or election row changes. */
    fun observe(electionId: Long): Flow<ElectionStats?> {
        val election = combine(
            electionDao.observeById(electionId),
            positionDao.observeByElection(electionId),
            participationDao.observeTurnoutByPosition(electionId)
        ) { e, p, t -> Triple(e, p, t) }

        val people = combine(
            participationDao.observeVotersByDepartment(electionId),
            voterDao.observeEligibleByDepartment(),
            departmentDao.observeAll()
        ) { voted, eligible, depts -> Triple(voted, eligible, depts) }

        return combine(election, people) { (e, positions, turnout), (votedByDept, eligibleByDept, depts) ->
            if (e == null) return@combine null

            val eligibleMap = eligibleByDept.associate { it.departmentId to it.total }
            val turnoutMap = turnout.associate { it.positionId to it.total }
            val votedMap = votedByDept.associate { it.departmentId to it.total }
            val allEligible = eligibleMap.values.sum()

            val positionStats = positions.map { p ->
                PositionStats(
                    title = p.title,
                    eligible = if (p.departmentId == null) allEligible else eligibleMap[p.departmentId] ?: 0,
                    voted = turnoutMap[p.id] ?: 0
                )
            }
            val openToAll = positions.any { it.departmentId == null }
            val relevant = if (openToAll) depts.map { it.id }.toSet() else positions.mapNotNull { it.departmentId }.toSet()
            val departmentStats = depts.filter { it.id in relevant }.map {
                DepartmentStats(it.name, eligibleMap[it.id] ?: 0, votedMap[it.id] ?: 0)
            }

            ElectionStats(
                electionTitle = e.title,
                status = e.status,
                endTime = e.endTime,
                overallEligible = departmentStats.sumOf { it.eligible },
                overallVoted = departmentStats.sumOf { it.voted },
                positions = positionStats,
                departments = departmentStats
            )
        }
    }
}