package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.local.entity.NotificationType
import com.univote.evoting.data.repository.BallotLoadResult
import com.univote.evoting.data.repository.CastVoteResult
import com.univote.evoting.data.repository.NotificationRepository
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.data.repository.StatisticsRepository
import com.univote.evoting.data.repository.VotingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsAndNotificationsTest {

    private lateinit var db: UniVoteDatabase
    private var electionId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
        electionId = db.electionDao().observeAll().first().first().id
        db.electionDao().updateStatus(electionId, ElectionStatus.OPEN)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun session(username: String): SessionUser {
        val u = db.userDao().findByUsername(username)!!
        return SessionUser(u.id, u.username, u.displayName, u.role, u.voterRegNumber)
    }

    private suspend fun voteAs(username: String) {
        val s = session(username)
        val repo = VotingRepository(db, AuditLogger(db)) { s }
        val form = (repo.loadBallot(electionId) as BallotLoadResult.Ready).form
        val picks: Map<Long, Long?> = form.positions.associate { it.positionId to it.candidates.first().id }
        assertTrue(repo.castVote(electionId, picks) is CastVoteResult.Success)
    }

    @Test
    fun stats_countParticipation_overallPositionAndDepartment() = runBlocking {
        voteAs("CS/0001/2024")
        val stats = StatisticsRepository(db).observe(electionId).first()!!

        assertEquals(11, stats.overallEligible) // 12 on the roll, 1 ineligible
        assertEquals(1, stats.overallVoted)

        val president = stats.positions.first { it.title == "President" }
        assertEquals(11, president.eligible)
        assertEquals(1, president.voted)

        val csRep = stats.positions.first { it.title.endsWith("Computer Science") }
        assertEquals(3, csRep.eligible)
        assertEquals(1, csRep.voted)

        assertEquals(1, stats.departments.first { it.name == "Computer Science" }.voted)
        assertEquals(0, stats.departments.first { it.name == "Business Administration" }.voted)
    }

    @Test
    fun stats_updateAfterEachVote() = runBlocking {
        val repo = StatisticsRepository(db)
        voteAs("CS/0001/2024")
        assertEquals(1, repo.observe(electionId).first()!!.overallVoted)
        voteAs("BBA/0001/2024")
        assertEquals(2, repo.observe(electionId).first()!!.overallVoted)
    }

    @Test
    fun voting_createsOneUnreadConfirmation_forThatVoterOnly() = runBlocking {
        voteAs("CS/0001/2024")
        val cs = session("CS/0001/2024")
        val bba = session("BBA/0001/2024")

        val mine = NotificationRepository(db) { cs }
        val theirs = NotificationRepository(db) { bba }

        assertEquals(1, mine.observeUnreadCount().first())
        assertEquals(0, theirs.observeUnreadCount().first())
        val alert = mine.observeMine().first().first()
        assertEquals(NotificationType.VOTE_CONFIRMATION, alert.type)
        assertTrue(alert.message.contains("Receipt code"))
    }

    @Test
    fun markRead_isScopedToTheSignedInUser() = runBlocking {
        voteAs("CS/0001/2024")
        val cs = session("CS/0001/2024")
        val bba = session("BBA/0001/2024")
        val mine = NotificationRepository(db) { cs }
        val intruder = NotificationRepository(db) { bba }
        val alertId = mine.observeMine().first().first().id

        intruder.markRead(alertId)
        assertEquals(1, mine.observeUnreadCount().first()) // untouched

        mine.markRead(alertId)
        assertEquals(0, mine.observeUnreadCount().first())
    }

    @Test
    fun markAllRead_clearsEveryUnreadAlert() = runBlocking {
        voteAs("CS/0001/2024")
        val cs = session("CS/0001/2024")
        val mine = NotificationRepository(db) { cs }
        db.notificationDao().insert(
            com.univote.evoting.data.local.entity.NotificationEntity(
                userId = cs.id, title = "Extra", message = "Another alert"
            )
        )
        assertNotNull(mine.observeMine().first().firstOrNull())
        assertEquals(2, mine.observeUnreadCount().first())
        mine.markAllRead()
        assertEquals(0, mine.observeUnreadCount().first())
    }
}