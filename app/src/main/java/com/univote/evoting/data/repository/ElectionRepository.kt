package com.univote.evoting.data.repository

import com.univote.evoting.core.util.DateFormats
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.CandidateStatus
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.NotificationType
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.domain.ElectionStateMachine
import kotlinx.coroutines.flow.Flow

class ElectionRepository(
    db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val actor: () -> String
) {
    private val electionDao = db.electionDao()
    private val positionDao = db.positionDao()
    private val candidateDao = db.candidateDao()
    private val userDao = db.userDao()
    private val notificationDao = db.notificationDao()

    fun observeAll(): Flow<List<ElectionEntity>> = electionDao.observeAll()
    fun observe(id: Long): Flow<ElectionEntity?> = electionDao.observeById(id)
    fun observePositions(electionId: Long): Flow<List<PositionEntity>> = positionDao.observeByElection(electionId)
    fun observeCandidates(electionId: Long): Flow<List<CandidateEntity>> = candidateDao.observeByElection(electionId)

    // ---- Elections ----

    suspend fun create(title: String, description: String, start: Long, end: Long): ActionResult {
        electionProblem(title, start, end)?.let { return ActionResult.Failure(it) }
        val id = electionDao.insert(
            ElectionEntity(
                title = title.trim(), description = description.trim(),
                startTime = start, endTime = end, status = ElectionStatus.DRAFT
            )
        )
        audit.log(actor(), "ELECTION_CREATED", "id=$id title=${title.trim()}")
        return ActionResult.Success
    }

    suspend fun update(id: Long, title: String, description: String, start: Long, end: Long): ActionResult {
        val election = electionDao.findById(id) ?: return ActionResult.Failure("Election not found.")
        if (!ElectionStateMachine.isEditable(election.status)) return ActionResult.Failure(LOCKED)
        electionProblem(title, start, end)?.let { return ActionResult.Failure(it) }
        electionDao.update(
            election.copy(title = title.trim(), description = description.trim(), startTime = start, endTime = end)
        )
        audit.log(actor(), "ELECTION_UPDATED", "id=$id")
        if (election.status == ElectionStatus.SCHEDULED) {
            notifyVoters(
                "Election schedule changed",
                "\"${title.trim()}\" now runs from ${DateFormats.format(start)} to ${DateFormats.format(end)}."
            )
        }
        return ActionResult.Success
    }

    suspend fun changeStatus(electionId: Long, target: ElectionStatus): ActionResult {
        val election = electionDao.findById(electionId) ?: return ActionResult.Failure("Election not found.")
        if (!ElectionStateMachine.canTransition(election.status, target)) {
            return ActionResult.Failure("An election cannot move from ${election.status} to $target.")
        }
        val now = System.currentTimeMillis()

        val publishing = target == ElectionStatus.SCHEDULED
        val openingFirstTime = target == ElectionStatus.OPEN && election.status == ElectionStatus.SCHEDULED
        if (publishing || openingFirstTime) {
            readinessProblem(electionId)?.let { return ActionResult.Failure(it) }
        }
        if (target == ElectionStatus.OPEN) {
            if (openingFirstTime && now < election.startTime) {
                return ActionResult.Failure(
                    "Voting cannot open before the scheduled start (${DateFormats.format(election.startTime)})."
                )
            }
            if (now >= election.endTime) {
                return ActionResult.Failure("The scheduled end time has passed. Edit the schedule first.")
            }
        }

        electionDao.updateStatus(electionId, target)
        audit.log(actor(), "ELECTION_STATUS_CHANGED", "id=$electionId from=${election.status} to=$target")

        val name = "\"${election.title}\""
        when (target) {
            ElectionStatus.SCHEDULED -> notifyVoters(
                "Upcoming election",
                "$name runs from ${DateFormats.format(election.startTime)} to ${DateFormats.format(election.endTime)}."
            )
            ElectionStatus.OPEN -> notifyVoters(
                if (election.status == ElectionStatus.SUSPENDED) "Voting resumed" else "Voting is open",
                "Voting for $name is open until ${DateFormats.format(election.endTime)}."
            )
            ElectionStatus.SUSPENDED -> notifyVoters(
                "Voting suspended", "Voting for $name is temporarily suspended. You will be told when it resumes."
            )
            ElectionStatus.CLOSED -> notifyVoters(
                "Voting closed", "Voting for $name has closed. Results will be announced once verified."
            )
            else -> Unit
        }
        return ActionResult.Success
    }

    /** Closes any OPEN/SUSPENDED election whose end time has passed. Safe to call often. */
    suspend fun closeExpired() {
        electionDao.findExpired(System.currentTimeMillis()).forEach { e ->
            electionDao.updateStatus(e.id, ElectionStatus.CLOSED)
            audit.log("system", "ELECTION_AUTO_CLOSED", "id=${e.id}")
            notifyVoters("Voting closed", "Voting for \"${e.title}\" has closed.")
        }
    }

    // ---- Positions ----

    suspend fun addPosition(electionId: Long, title: String, departmentId: Long?): ActionResult {
        lockedFailure(electionId)?.let { return it }
        if (title.isBlank()) return ActionResult.Failure("Enter the position title.")
        val id = positionDao.insert(PositionEntity(electionId = electionId, title = title.trim(), departmentId = departmentId))
        audit.log(actor(), "POSITION_ADDED", "id=$id election=$electionId title=${title.trim()}")
        return ActionResult.Success
    }

    suspend fun deletePosition(positionId: Long): ActionResult {
        val position = positionDao.findById(positionId) ?: return ActionResult.Failure("Position not found.")
        lockedFailure(position.electionId)?.let { return it }
        positionDao.delete(position)
        audit.log(actor(), "POSITION_DELETED", "id=$positionId title=${position.title}")
        return ActionResult.Success
    }

    // ---- Candidates ----

    suspend fun addCandidate(positionId: Long, partyId: Long?, name: String, manifesto: String): ActionResult {
        val position = positionDao.findById(positionId) ?: return ActionResult.Failure("Position not found.")
        lockedFailure(position.electionId)?.let { return it }
        candidateProblem(name, manifesto)?.let { return ActionResult.Failure(it) }
        val id = candidateDao.insert(
            CandidateEntity(
                positionId = positionId, partyId = partyId, fullName = name.trim(),
                manifesto = manifesto.trim(), status = CandidateStatus.PENDING
            )
        )
        audit.log(actor(), "CANDIDATE_ADDED", "id=$id position=$positionId name=${name.trim()}")
        return ActionResult.Success
    }

    /** Editing a candidate sends them back to PENDING so the change is re-verified. */
    suspend fun updateCandidate(candidateId: Long, partyId: Long?, name: String, manifesto: String): ActionResult {
        val candidate = candidateDao.findById(candidateId) ?: return ActionResult.Failure("Candidate not found.")
        lockedFailure(electionOf(candidate))?.let { return it }
        candidateProblem(name, manifesto)?.let { return ActionResult.Failure(it) }
        candidateDao.update(
            candidate.copy(
                partyId = partyId, fullName = name.trim(), manifesto = manifesto.trim(),
                status = CandidateStatus.PENDING
            )
        )
        audit.log(actor(), "CANDIDATE_UPDATED", "id=$candidateId")
        return ActionResult.Success
    }

    suspend fun setCandidateStatus(candidateId: Long, status: CandidateStatus): ActionResult {
        val candidate = candidateDao.findById(candidateId) ?: return ActionResult.Failure("Candidate not found.")
        lockedFailure(electionOf(candidate))?.let { return it }
        candidateDao.update(candidate.copy(status = status))
        audit.log(actor(), "CANDIDATE_${status.name}", "id=$candidateId name=${candidate.fullName}")
        return ActionResult.Success
    }

    suspend fun deleteCandidate(candidateId: Long): ActionResult {
        val candidate = candidateDao.findById(candidateId) ?: return ActionResult.Failure("Candidate not found.")
        lockedFailure(electionOf(candidate))?.let { return it }
        candidateDao.delete(candidate)
        audit.log(actor(), "CANDIDATE_DELETED", "id=$candidateId name=${candidate.fullName}")
        return ActionResult.Success
    }

    // ---- Helpers ----

    private suspend fun notifyVoters(title: String, message: String) {
        val now = System.currentTimeMillis()
        notificationDao.insertAll(
            userDao.listIdsByRoles(listOf(Role.VOTER.name)).map {
                NotificationEntity(
                    userId = it, title = title, message = message,
                    type = NotificationType.SCHEDULE, createdAt = now
                )
            }
        )
    }

    private suspend fun electionOf(candidate: CandidateEntity): Long =
        positionDao.findById(candidate.positionId)?.electionId ?: -1L

    private suspend fun lockedFailure(electionId: Long): ActionResult.Failure? {
        val election = electionDao.findById(electionId) ?: return ActionResult.Failure("Election not found.")
        return if (ElectionStateMachine.isEditable(election.status)) null else ActionResult.Failure(LOCKED)
    }

    private suspend fun readinessProblem(electionId: Long): String? {
        val positions = positionDao.listByElection(electionId)
        if (positions.isEmpty()) return "Add at least one position first."
        positions.forEach { p ->
            if (candidateDao.countApprovedByPosition(p.id) == 0) {
                return "Position \"${p.title}\" has no approved candidates."
            }
        }
        return null
    }

    private fun electionProblem(title: String, start: Long, end: Long): String? = when {
        title.isBlank() -> "Enter an election title."
        end <= start -> "The end time must be after the start time."
        else -> null
    }

    private fun candidateProblem(name: String, manifesto: String): String? = when {
        name.isBlank() -> "Enter the candidate's name."
        manifesto.isBlank() -> "Enter a short manifesto."
        else -> null
    }

    private companion object {
        const val LOCKED = "Voting has started or ended, so the election setup is locked."
    }
}