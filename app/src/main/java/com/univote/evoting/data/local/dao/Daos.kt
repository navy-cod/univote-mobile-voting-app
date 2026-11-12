package com.univote.evoting.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.univote.evoting.data.local.entity.AuditLogEntity
import com.univote.evoting.data.local.entity.BallotEntity
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.ParticipationEntity
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.local.entity.UserEntity
import com.univote.evoting.data.local.entity.VoterEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.local.entity.ResultEntity
import com.univote.evoting.data.local.entity.SupportTicketEntity
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.local.entity.TicketType
import kotlinx.coroutines.flow.Flow

data class TallyRow(val positionId: Long, val candidateId: Long?, val votes: Int)
data class PositionCount(val positionId: Long, val total: Int)
data class DepartmentCount(val departmentId: Long, val total: Int)

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?
    @Query("SELECT id FROM users WHERE role IN ('VOTER', 'OBSERVER') AND isActive = 1")
    suspend fun listRecipientIds(): List<Long>
    @Query("SELECT id FROM users WHERE role IN (:roles) AND isActive = 1")
    suspend fun listIdsByRoles(roles: List<String>): List<Long>
    @Update
    suspend fun update(user: UserEntity)
    @Query("SELECT COUNT(*) FROM users")
    fun observeCount(): Flow<Int>
}

@Dao
interface DepartmentDao {
    @Insert suspend fun insert(department: DepartmentEntity): Long
    @Update suspend fun update(department: DepartmentEntity)
    @Delete suspend fun delete(department: DepartmentEntity)

    @Query("SELECT * FROM departments ORDER BY name")
    fun observeAll(): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments WHERE code = :code COLLATE NOCASE LIMIT 1")
    suspend fun findByCode(code: String): DepartmentEntity?

    @Query("SELECT COUNT(*) FROM departments") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM departments") fun observeCount(): Flow<Int>
}

@Dao
interface VoterDao {
    @Insert suspend fun insert(voter: VoterEntity)
    @Insert suspend fun insertAll(voters: List<VoterEntity>)
    @Update suspend fun update(voter: VoterEntity)

    @Query("SELECT * FROM voters ORDER BY regNumber")
    fun observeAll(): Flow<List<VoterEntity>>

    @Query("SELECT * FROM voters WHERE regNumber = :regNumber LIMIT 1")
    suspend fun findByRegNumber(regNumber: String): VoterEntity?

    @Query("SELECT COUNT(*) FROM voters WHERE departmentId = :departmentId")
    suspend fun countByDepartment(departmentId: Long): Int
    @Query("SELECT departmentId AS departmentId, COUNT(*) AS total FROM voters WHERE isEligible = 1 GROUP BY departmentId")
    fun observeEligibleByDepartment(): Flow<List<DepartmentCount>>

    @Query("SELECT COUNT(*) FROM voters") fun observeCount(): Flow<Int>
}

@Dao
interface PartyDao {
    @Insert suspend fun insert(party: PoliticalPartyEntity): Long
    @Update suspend fun update(party: PoliticalPartyEntity)
    @Delete suspend fun delete(party: PoliticalPartyEntity)

    @Query("SELECT * FROM political_parties ORDER BY name")
    fun observeAll(): Flow<List<PoliticalPartyEntity>>

    @Query("SELECT * FROM political_parties WHERE acronym = :acronym COLLATE NOCASE LIMIT 1")
    suspend fun findByAcronym(acronym: String): PoliticalPartyEntity?
    @Query("SELECT * FROM political_parties")
    suspend fun listAll(): List<PoliticalPartyEntity>
    @Query("SELECT COUNT(*) FROM political_parties") fun observeCount(): Flow<Int>

}

@Dao
interface ElectionDao {
    @Insert suspend fun insert(election: ElectionEntity): Long
    @Update suspend fun update(election: ElectionEntity)

    @Query("UPDATE elections SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ElectionStatus)

    @Query("SELECT * FROM elections WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ElectionEntity?

    @Query("SELECT * FROM elections WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ElectionEntity?>
    @Query("UPDATE elections SET resultsPublished = 1 WHERE id = :id")
    suspend fun markResultsPublished(id: Long)


    @Query("SELECT * FROM elections ORDER BY startTime DESC")
    fun observeAll(): Flow<List<ElectionEntity>>

    @Query("SELECT * FROM elections WHERE status IN ('OPEN', 'SUSPENDED') AND endTime <= :now")
    suspend fun findExpired(now: Long): List<ElectionEntity>

    @Query("SELECT * FROM elections WHERE status != 'DRAFT' ORDER BY startTime DESC")
    fun observeVisible(): Flow<List<ElectionEntity>>
    @Query("SELECT COUNT(*) FROM elections") fun observeCount(): Flow<Int>
}

@Dao
interface PositionDao {
    @Insert suspend fun insert(position: PositionEntity): Long
    @Delete suspend fun delete(position: PositionEntity)

    @Query("SELECT * FROM positions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PositionEntity?

    @Query("SELECT * FROM positions WHERE electionId = :electionId ORDER BY id")
    fun observeByElection(electionId: Long): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE electionId = :electionId ORDER BY id")
    suspend fun listByElection(electionId: Long): List<PositionEntity>

    @Query("SELECT COUNT(*) FROM positions WHERE departmentId = :departmentId")
    suspend fun countByDepartment(departmentId: Long): Int

    @Query(
        "SELECT * FROM positions WHERE electionId = :electionId " +
                "AND (departmentId IS NULL OR departmentId = :departmentId) ORDER BY id"
    )
    suspend fun listForDepartment(electionId: Long, departmentId: Long): List<PositionEntity>

    @Query("SELECT COUNT(*) FROM positions") fun observeCount(): Flow<Int>
}

@Dao
interface CandidateDao {
    @Insert suspend fun insert(candidate: CandidateEntity): Long
    @Update suspend fun update(candidate: CandidateEntity)
    @Delete suspend fun delete(candidate: CandidateEntity)

    @Query("SELECT * FROM candidates WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CandidateEntity?

    @Query(
        "SELECT c.* FROM candidates c INNER JOIN positions p ON p.id = c.positionId " +
                "WHERE p.electionId = :electionId ORDER BY c.id"
    )
    fun observeByElection(electionId: Long): Flow<List<CandidateEntity>>

    @Query("SELECT COUNT(*) FROM candidates WHERE positionId = :positionId AND status = 'APPROVED'")
    suspend fun countApprovedByPosition(positionId: Long): Int

    @Query("SELECT COUNT(*) FROM candidates WHERE partyId = :partyId")
    suspend fun countByParty(partyId: Long): Int

    @Query("SELECT * FROM candidates WHERE positionId = :positionId AND status = 'APPROVED' ORDER BY fullName")
    suspend fun listApprovedByPosition(positionId: Long): List<CandidateEntity>
    @Query(
        "SELECT c.* FROM candidates c INNER JOIN positions p ON p.id = c.positionId " +
                "WHERE p.electionId = :electionId ORDER BY c.id"
    )
    suspend fun listByElection(electionId: Long): List<CandidateEntity>

    @Query("SELECT COUNT(*) FROM candidates") fun observeCount(): Flow<Int>
}

@Dao
interface AuditLogDao {
    @Insert suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 1")
    suspend fun last(): AuditLogEntity?

    @Query("SELECT * FROM audit_logs ORDER BY id")
    suspend fun all(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun count(): Int
}

@Dao
interface BallotDao {
    @Insert suspend fun insert(ballot: BallotEntity): Long

    @Query("SELECT COUNT(*) FROM ballots WHERE electionId = :electionId")
    suspend fun countForElection(electionId: Long): Int
    @Query(
        "SELECT positionId AS positionId, candidateId AS candidateId, COUNT(*) AS votes " +
                "FROM ballots WHERE electionId = :electionId GROUP BY positionId, candidateId"
    )
    suspend fun tally(electionId: Long): List<TallyRow>
}

@Dao
interface ParticipationDao {
    @Insert suspend fun insert(participation: ParticipationEntity): Long

    @Query("SELECT * FROM participation WHERE voterRegNumber = :reg ORDER BY id")
    fun observeByVoter(reg: String): Flow<List<ParticipationEntity>>

    @Query("SELECT * FROM participation WHERE electionId = :electionId AND voterRegNumber = :reg ORDER BY id")
    suspend fun listForVoter(electionId: Long, reg: String): List<ParticipationEntity>

    @Query("SELECT COUNT(*) FROM participation WHERE electionId = :electionId")
    suspend fun countForElection(electionId: Long): Int
    @Query("SELECT positionId AS positionId, COUNT(*) AS total FROM participation WHERE electionId = :electionId GROUP BY positionId")
    suspend fun turnoutByPosition(electionId: Long): List<PositionCount>

    @Query("SELECT positionId AS positionId, COUNT(*) AS total FROM participation WHERE electionId = :electionId GROUP BY positionId")
    fun observeTurnoutByPosition(electionId: Long): Flow<List<PositionCount>>

    @Query(
        "SELECT v.departmentId AS departmentId, COUNT(DISTINCT p.voterRegNumber) AS total " +
                "FROM participation p INNER JOIN voters v ON v.regNumber = p.voterRegNumber " +
                "WHERE p.electionId = :electionId GROUP BY v.departmentId"
    )
    fun observeVotersByDepartment(electionId: Long): Flow<List<DepartmentCount>>
}

@Dao
interface NotificationDao {
    @Insert suspend fun insert(notification: NotificationEntity): Long
    @Insert suspend fun insertAll(notifications: List<NotificationEntity>)

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY id DESC")
    fun observeForUser(userId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND isRead = 0 ORDER BY id")
    fun observeUnread(userId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnreadCount(userId: Long): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id AND userId = :userId")
    suspend fun markRead(id: Long, userId: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: Long)
}

@Dao
interface ResultDao {
    @Insert suspend fun insertAll(results: List<ResultEntity>)

    @Query("SELECT * FROM results WHERE electionId = :electionId ORDER BY positionId, votes DESC")
    suspend fun listByElection(electionId: Long): List<ResultEntity>
}

data class TicketRow(
    val id: Long,
    val referenceNo: String,
    val reporterUserId: Long,
    val reporterName: String,
    val type: TicketType,
    val subject: String,
    val description: String,
    val evidenceUri: String?,
    val status: TicketStatus,
    val assignedToName: String?,
    val resolutionNote: String?,
    val createdAt: Long,
    val updatedAt: Long
)

private const val TICKET_SELECT =
    "SELECT t.id AS id, t.referenceNo AS referenceNo, t.reporterUserId AS reporterUserId, " +
            "r.displayName AS reporterName, t.type AS type, t.subject AS subject, t.description AS description, " +
            "t.evidenceUri AS evidenceUri, t.status AS status, a.displayName AS assignedToName, " +
            "t.resolutionNote AS resolutionNote, t.createdAt AS createdAt, t.updatedAt AS updatedAt " +
            "FROM support_tickets t INNER JOIN users r ON r.id = t.reporterUserId " +
            "LEFT JOIN users a ON a.id = t.assignedToUserId"

@Dao
interface SupportTicketDao {
    @Insert suspend fun insert(ticket: SupportTicketEntity): Long
    @Update suspend fun update(ticket: SupportTicketEntity)

    @Query("SELECT * FROM support_tickets WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SupportTicketEntity?

    @Query(TICKET_SELECT + " WHERE t.reporterUserId = :userId ORDER BY t.id DESC")
    fun observeMine(userId: Long): Flow<List<TicketRow>>

    @Query(TICKET_SELECT + " ORDER BY t.id DESC")
    fun observeAll(): Flow<List<TicketRow>>

    @Query(TICKET_SELECT + " WHERE t.id = :id")
    fun observeRow(id: Long): Flow<TicketRow?>
}

@Dao
interface SupportTicketDao {
    @Insert
    suspend fun insert(ticket: com.univote.evoting.data.local.entity.SupportTicketEntity): Long

    @Query("SELECT * FROM support_tickets WHERE reporterUserId = :userId ORDER BY createdAt DESC")
    fun observeForReporter(userId: Long): Flow<List<com.univote.evoting.data.local.entity.SupportTicketEntity>>

    @Query("SELECT * FROM support_tickets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<com.univote.evoting.data.local.entity.SupportTicketEntity>>

    @Query("SELECT * FROM support_tickets WHERE referenceNo = :referenceNo LIMIT 1")
    suspend fun findByReference(referenceNo: String): com.univote.evoting.data.local.entity.SupportTicketEntity?

    @Query("UPDATE support_tickets SET status = :status, resolutionNote = :resolutionNote, updatedAt = :updatedAt WHERE id = :ticketId")
    suspend fun updateStatus(
        ticketId: Long,
        status: com.univote.evoting.data.local.entity.TicketStatus,
        resolutionNote: String?,
        updatedAt: Long
    ): Int
}
