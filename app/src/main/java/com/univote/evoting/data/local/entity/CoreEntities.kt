package com.univote.evoting.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "departments",
    indices = [Index(value = ["code"], unique = true)]
)
data class DepartmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String
)

/** The approved student roll. A voter must exist here (and be eligible) before an account can be created. */
@Entity(
    tableName = "voters",
    foreignKeys = [
        ForeignKey(
            entity = DepartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("departmentId")]
)
data class VoterEntity(
    @PrimaryKey val regNumber: String,
    val fullName: String,
    val departmentId: Long,
    val yearOfStudy: Int,
    val isEligible: Boolean = true
)

/** A login account. Voters link to the roll through [voterRegNumber]; staff accounts leave it null. */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val displayName: String,
    val role: Role,
    val passwordHash: String,
    val salt: String,
    val voterRegNumber: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
