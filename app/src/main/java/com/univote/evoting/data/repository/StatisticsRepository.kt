package com.univote.evoting.data.repository

import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.ElectionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class PositionStats(val title: String, val eligible: Int, val voted: Int, val hidden: Boolean = false)
data class DepartmentStats(val name: String, val eligible: Int, val voted: Int, val hidden: Boolean = false)

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

            val rawPositions = positions.map { p ->
                Triple(
                    p.title,
                    if (p.departmentId == null) allEligible else eligibleMap[p.departmentId] ?: 0,
                    turnoutMap[p.id] ?: 0
                )
            }
            val openToAll = positions.any { it.departmentId == null }
            val relevant =
                if (openToAll) depts.map { it.id }.toSet() else positions.mapNotNull { it.departmentId }.toSet()
            val rawDepartments = depts.filter { it.id in relevant }.map {
                Triple(it.name, eligibleMap[it.id] ?: 0, votedMap[it.id] ?: 0)
            }

            ElectionStats(
                electionTitle = e.title,
                status = e.status,
                endTime = e.endTime,
                overallEligible = rawDepartments.sumOf { it.second },
                overallVoted = rawDepartments.sumOf { it.third },
                positions = rawPositions.map { (title, eligible, voted) ->
                    if (eligible < MIN_GROUP_SIZE) PositionStats(title, 0, 0, hidden = true)
                    else PositionStats(title, eligible, voted)
                },
                departments = rawDepartments.map { (name, eligible, voted) ->
                    if (eligible < MIN_GROUP_SIZE) DepartmentStats(name, 0, 0, hidden = true)
                    else DepartmentStats(name, eligible, voted)
                }
            )
        }
    }

    companion object {
        /** Groups smaller than this are hidden to protect voter privacy. Use a larger value in production. */
        const val MIN_GROUP_SIZE = 3
    }
}