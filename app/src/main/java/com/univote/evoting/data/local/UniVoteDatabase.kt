package com.univote.evoting.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.univote.evoting.data.local.dao.AuditLogDao
import com.univote.evoting.data.local.dao.CandidateDao
import com.univote.evoting.data.local.dao.DepartmentDao
import com.univote.evoting.data.local.dao.ElectionDao
import com.univote.evoting.data.local.dao.PartyDao
import com.univote.evoting.data.local.dao.PositionDao
import com.univote.evoting.data.local.dao.ResultDao
import com.univote.evoting.data.local.dao.SupportTicketDao
import com.univote.evoting.data.local.dao.UserDao
import com.univote.evoting.data.local.dao.VoterDao
import com.univote.evoting.data.local.entity.AuditLogEntity
import com.univote.evoting.data.local.entity.BallotEntity
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.FeedbackEntity
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.ParticipationEntity
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.local.entity.ResultEntity
import com.univote.evoting.data.local.entity.SupportTicketEntity
import com.univote.evoting.data.local.entity.UserEntity
import com.univote.evoting.data.local.entity.VoterEntity
import com.univote.evoting.data.local.dao.BallotDao
import com.univote.evoting.data.local.dao.NotificationDao
import com.univote.evoting.data.local.dao.ParticipationDao

@Database(
    entities = [
        DepartmentEntity::class,
        VoterEntity::class,
        UserEntity::class,
        PoliticalPartyEntity::class,
        ElectionEntity::class,
        PositionEntity::class,
        CandidateEntity::class,
        BallotEntity::class,
        ParticipationEntity::class,
        ResultEntity::class,
        NotificationEntity::class,
        FeedbackEntity::class,
        AuditLogEntity::class,
        SupportTicketEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class UniVoteDatabase : RoomDatabase() {
    abstract fun departmentDao(): DepartmentDao
    abstract fun voterDao(): VoterDao
    abstract fun userDao(): UserDao
    abstract fun partyDao(): PartyDao
    abstract fun electionDao(): ElectionDao
    abstract fun positionDao(): PositionDao
    abstract fun candidateDao(): CandidateDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun ballotDao(): BallotDao
    abstract fun participationDao(): ParticipationDao
    abstract fun notificationDao(): NotificationDao
    abstract fun resultDao(): ResultDao
    abstract fun supportTicketDao(): SupportTicketDao

    companion object {
        const val NAME = "univote.db"
    }
}
