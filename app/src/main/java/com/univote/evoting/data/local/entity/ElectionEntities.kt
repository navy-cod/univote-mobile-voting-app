package com.univote.evoting.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "political_parties",
    indices = [Index(value = ["acronym"], unique = true)]
)
data class PoliticalPartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val acronym: String,
    val slogan: String? = null
)

@Entity(tableName = "elections")
data class ElectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val status: ElectionStatus = ElectionStatus.DRAFT,
    val resultsPublished: Boolean = false
)

/** A contested post. [departmentId] = null means every department may vote for it. */
@Entity(
    tableName = "positions",
    foreignKeys = [
        ForeignKey(
            entity = ElectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["electionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DepartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("electionId"), Index("departmentId")]
)
data class PositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val electionId: Long,
    val title: String,
    val departmentId: Long? = null
)

@Entity(
    tableName = "candidates",
    foreignKeys = [
        ForeignKey(
            entity = PositionEntity::class,
            parentColumns = ["id"],
            childColumns = ["positionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PoliticalPartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("positionId"), Index("partyId")]
)
data class CandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val positionId: Long,
    val partyId: Long? = null,
    val fullName: String,
    val manifesto: String,
    val photoUri: String? = null,
    val status: CandidateStatus = CandidateStatus.PENDING
)
