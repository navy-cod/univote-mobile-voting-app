package com.univote.evoting.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.local.entity.UserEntity
import com.univote.evoting.data.local.entity.VoterEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Update

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

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT id FROM users WHERE role = :role AND isActive = 1")
    suspend fun findActiveIdsByRole(role: Role): List<Long>

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

/** Every query is scoped by userId so one account can never read or change another account's inbox. */
@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Insert
    suspend fun insertAll(notifications: List<NotificationEntity>): List<Long>

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC, id DESC")
    fun observeForUser(userId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnreadCount(userId: Long): Flow<Int>

    @Query("SELECT id FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun unreadIds(userId: Long): List<Long>

    @Query("SELECT id FROM notifications WHERE userId = :userId")
    suspend fun allIds(userId: Long): List<Long>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id AND userId = :userId")
    suspend fun markRead(id: Long, userId: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId AND isRead = 0")
    suspend fun markAllRead(userId: Long)

    @Query("DELETE FROM notifications WHERE id = :id AND userId = :userId")
    suspend fun delete(id: Long, userId: Long)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteAll(userId: Long)
}
