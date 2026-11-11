package com.univote.evoting.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.univote.evoting.data.local.entity.AuditLogEntity
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.local.entity.UserEntity
import com.univote.evoting.data.local.entity.VoterEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

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

    @Query("SELECT * FROM elections ORDER BY startTime DESC")
    fun observeAll(): Flow<List<ElectionEntity>>

    @Query("SELECT * FROM elections WHERE status IN ('OPEN', 'SUSPENDED') AND endTime <= :now")
    suspend fun findExpired(now: Long): List<ElectionEntity>

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

    @Query("SELECT COUNT(*) FROM candidates") fun observeCount(): Flow<Int>
}

@Dao
interface AuditLogDao {
    @Insert suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 1")
    suspend fun last(): AuditLogEntity?

    @Query("SELECT * FROM audit_logs ORDER BY id")
    suspend fun all(): List<AuditLogEntity>
}
