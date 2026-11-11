package com.univote.evoting.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PRIVACY RULE: a ballot has NO voter id and NO timestamp.
 * Who voted lives in [ParticipationEntity]; what was chosen lives here. Nothing links the two.
 * [candidateId] = null records a blank / abstain choice.
 */
@Entity(
    tableName = "ballots",
    foreignKeys = [
        ForeignKey(
            entity = ElectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["electionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PositionEntity::class,
            parentColumns = ["id"],
            childColumns = ["positionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidateId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["ballotRef"], unique = true),
        Index(value = ["electionId", "positionId"]),
        Index("positionId"),
        Index("candidateId")
    ]
)
data class BallotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ballotRef: String,
    val electionId: Long,
    val positionId: Long,
    val candidateId: Long?
)

/** Records THAT a voter voted for a position (never WHOM). The unique index is the duplicate-vote guard. */
@Entity(
    tableName = "participation",
    foreignKeys = [
        ForeignKey(
            entity = ElectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["electionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PositionEntity::class,
            parentColumns = ["id"],
            childColumns = ["positionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["electionId", "positionId", "voterRegNumber"], unique = true),
        Index("positionId")
    ]
)
data class ParticipationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val electionId: Long,
    val positionId: Long,
    val voterRegNumber: String,
    val receiptCode: String,
    val votedAt: Long
)

/** Verified, published tallies (kept as history once results are approved). */
@Entity(
    tableName = "results",
    foreignKeys = [
        ForeignKey(
            entity = ElectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["electionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PositionEntity::class,
            parentColumns = ["id"],
            childColumns = ["positionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidateId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["electionId", "positionId"]), Index("positionId"), Index("candidateId")]
)
data class ResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val electionId: Long,
    val positionId: Long,
    val candidateId: Long?,
    val votes: Int,
    val isWinner: Boolean = false,
    val publishedAt: Long
)
