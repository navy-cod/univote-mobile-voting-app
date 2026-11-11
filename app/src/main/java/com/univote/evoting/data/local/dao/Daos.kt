package com.univote.evoting.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.local.entity.UserEntity
import com.univote.evoting.data.local.entity.VoterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DepartmentDao {
    @Insert
    suspend fun insert(department: DepartmentEntity): Long

    @Query("SELECT * FROM departments ORDER BY name")
    fun observeAll(): Flow<List<DepartmentEntity>>

    @Query("SELECT COUNT(*) FROM departments")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM departments")
    fun observeCount(): Flow<Int>
}

@Dao
interface VoterDao {
    @Insert
    suspend fun insertAll(voters: List<VoterEntity>)

    @Query("SELECT * FROM voters WHERE regNumber = :regNumber LIMIT 1")
    suspend fun findByRegNumber(regNumber: String): VoterEntity?

    @Query("SELECT COUNT(*) FROM voters")
    fun observeCount(): Flow<Int>
}

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    fun observeCount(): Flow<Int>
}

@Dao
interface PartyDao {
    @Insert
    suspend fun insert(party: PoliticalPartyEntity): Long

    @Query("SELECT COUNT(*) FROM political_parties")
    fun observeCount(): Flow<Int>
}

@Dao
interface ElectionDao {
    @Insert
    suspend fun insert(election: ElectionEntity): Long

    @Query("SELECT * FROM elections ORDER BY startTime DESC")
    fun observeAll(): Flow<List<ElectionEntity>>

    @Query("SELECT COUNT(*) FROM elections")
    fun observeCount(): Flow<Int>
}

@Dao
interface PositionDao {
    @Insert
    suspend fun insert(position: PositionEntity): Long

    @Query("SELECT COUNT(*) FROM positions")
    fun observeCount(): Flow<Int>
}

@Dao
interface CandidateDao {
    @Insert
    suspend fun insert(candidate: CandidateEntity): Long

    @Query("SELECT COUNT(*) FROM candidates")
    fun observeCount(): Flow<Int>
}
