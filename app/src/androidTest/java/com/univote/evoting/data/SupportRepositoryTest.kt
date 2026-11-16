package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.NotificationEntity
import com.univote.evoting.data.local.entity.TicketStatus
import com.univote.evoting.data.local.entity.TicketType
import com.univote.evoting.data.repository.ActionResult
import com.univote.evoting.data.repository.SessionUser
import com.univote.evoting.data.repository.SubmitResult
import com.univote.evoting.data.repository.SupportRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupportRepositoryTest {

    private lateinit var db: UniVoteDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), UniVoteDatabase::class.java
        ).build()
        DatabaseSeeder(db).seedIfEmpty()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun repoFor(username: String): SupportRepository {
        val u = db.userDao().findByUsername(username)!!
        val session = SessionUser(u.id, u.username, u.displayName, u.role, u.voterRegNumber)
        return SupportRepository(db, AuditLogger(db)) { session }
    }

    private suspend fun alerts(username: String): List<NotificationEntity> =
        db.notificationDao().observeForUser(db.userDao().findByUsername(username)!!.id).first()

    private suspend fun submitAs(
        username: String,
        type: TicketType = TicketType.TECHNICAL,
        subject: String = "App freezes at login",
        description: String = "The screen freezes after I tap sign in."
    ): String {
        val r = repoFor(username).submit(type, subject, description, null)
        return (r as SubmitResult.Success).referenceNo
    }

    private suspend fun ticketIdOf(username: String) = repoFor(username).observeMine().first().first().id

    @Test
    fun technicalReport_getsReference_andNotifiesSupportButNotAdmin() = runBlocking {
        val ref = submitAs("CS/0001/2024")
        assertTrue(Regex("SR-[A-Z2-9]{8}").matches(ref))
        assertTrue(alerts("CS/0001/2024").any { it.title == "Report received" && it.message.contains(ref) })
        assertTrue(alerts("support").any { it.message.contains(ref) })
        assertTrue(alerts("admin").none { it.message.contains(ref) })
    }

    @Test
    fun fraudReport_alsoNotifiesElectionAndSystemAdmins() = runBlocking {
        val ref = submitAs("CS/0001/2024", TicketType.FRAUD, "Someone voted for me", "A friend says my account was used.")
        assertTrue(alerts("admin").any { it.title == "Fraud report received" && it.message.contains(ref) })
        assertTrue(alerts("sysadmin").any { it.message.contains(ref) })
        assertTrue(alerts("support").any { it.message.contains(ref) })
    }

    @Test
    fun invalidReports_areRejected_andNothingIsStored() = runBlocking {
        val repo = repoFor("CS/0001/2024")
        assertTrue(repo.submit(TicketType.TECHNICAL, "Hi", "Long enough description here", null) is SubmitResult.Failure)
        assertTrue(repo.submit(TicketType.TECHNICAL, "Good subject", "short", null) is SubmitResult.Failure)
        assertTrue(db.supportTicketDao().observeAll().first().isEmpty())
    }

    @Test
    fun tickets_areVisibleOnlyToReporterAndStaff() = runBlocking {
        submitAs("CS/0001/2024")
        val id = ticketIdOf("CS/0001/2024")

        assertNotNull(repoFor("CS/0001/2024").observeTicket(id).first())
        assertNotNull(repoFor("support").observeTicket(id).first())
        assertNull(repoFor("BBA/0001/2024").observeTicket(id).first())
        assertTrue(repoFor("BBA/0001/2024").observeMine().first().isEmpty())
        assertTrue(repoFor("BBA/0001/2024").observeQueue().first().isEmpty())
        assertEquals(1, repoFor("support").observeQueue().first().size)
    }

    @Test
    fun voter_cannotAssignOrChangeStatus() = runBlocking {
        submitAs("CS/0001/2024")
        val id = ticketIdOf("CS/0001/2024")
        val voter = repoFor("CS/0001/2024")
        assertTrue(voter.assignToMe(id) is ActionResult.Failure)
        assertTrue(voter.changeStatus(id, TicketStatus.CLOSED, "closing my own report") is ActionResult.Failure)
    }

    @Test
    fun assigning_movesToReview_andTellsTheReporter() = runBlocking {
        val ref = submitAs("CS/0001/2024")
        val id = ticketIdOf("CS/0001/2024")
        assertEquals(ActionResult.Success, repoFor("support").assignToMe(id))

        val row = repoFor("support").observeTicket(id).first()!!
        assertEquals(TicketStatus.IN_REVIEW, row.status)
        assertEquals("Support Officer", row.assignedToName)
        assertTrue(alerts("CS/0001/2024").any { it.title == "Report $ref updated" })
    }

    @Test
    fun resolving_needsANote_andRecordsTheOutcome() = runBlocking {
        submitAs("CS/0001/2024")
        val id = ticketIdOf("CS/0001/2024")
        val support = repoFor("support")
        support.assignToMe(id)

        assertTrue(support.changeStatus(id, TicketStatus.RESOLVED, "") is ActionResult.Failure)
        assertEquals(ActionResult.Success, support.changeStatus(id, TicketStatus.RESOLVED, "Fixed in the latest build"))

        val mine = repoFor("CS/0001/2024").observeMine().first().first()
        assertEquals(TicketStatus.RESOLVED, mine.status)
        assertEquals("Fixed in the latest build", mine.resolutionNote)
    }

    @Test
    fun skippingReview_isRejected_andClosedIsFinal() = runBlocking {
        submitAs("CS/0001/2024")
        val id = ticketIdOf("CS/0001/2024")
        val support = repoFor("support")

        assertTrue(support.changeStatus(id, TicketStatus.RESOLVED, "Trying to skip review") is ActionResult.Failure)
        assertEquals(ActionResult.Success, support.changeStatus(id, TicketStatus.CLOSED, "Duplicate of an earlier report"))
        assertTrue(support.changeStatus(id, TicketStatus.IN_REVIEW, "Reopen it") is ActionResult.Failure)
        assertTrue(support.assignToMe(id) is ActionResult.Failure)
    }

    @Test
    fun auditEntries_neverContainTheReportText() = runBlocking {
        val secret = "my private description with details"
        submitAs("CS/0001/2024", description = secret)
        val entries = db.auditLogDao().all().filter { it.action == "TICKET_CREATED" }
        assertEquals(1, entries.size)
        assertFalse(entries.first().details.contains(secret))
        assertTrue(entries.first().details.startsWith("ref=SR-"))
    }
}