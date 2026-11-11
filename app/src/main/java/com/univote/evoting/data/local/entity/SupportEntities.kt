package com.univote.evoting.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "feedback",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val rating: Int,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Tamper-evident log: each entry stores the hash of the previous one (a simple hash chain). No FKs so logs outlive deleted users. */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val actor: String,
    val action: String,
    val details: String,
    val previousHash: String,
    val entryHash: String
)

@Entity(
    tableName = "support_tickets",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["reporterUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["referenceNo"], unique = true), Index("reporterUserId")]
)
data class SupportTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val referenceNo: String,
    val reporterUserId: Long,
    val type: TicketType,
    val subject: String,
    val description: String,
    val evidenceUri: String? = null,
    val status: TicketStatus = TicketStatus.OPEN,
    val assignedToUserId: Long? = null,
    val resolutionNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
