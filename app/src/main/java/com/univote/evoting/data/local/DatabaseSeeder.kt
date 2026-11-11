package com.univote.evoting.data.local

import androidx.room.withTransaction
import com.univote.evoting.core.security.PasswordHasher
import com.univote.evoting.data.local.entity.CandidateEntity
import com.univote.evoting.data.local.entity.CandidateStatus
import com.univote.evoting.data.local.entity.DepartmentEntity
import com.univote.evoting.data.local.entity.ElectionEntity
import com.univote.evoting.data.local.entity.ElectionStatus
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import com.univote.evoting.data.local.entity.PositionEntity
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.local.entity.UserEntity
import com.univote.evoting.data.local.entity.VoterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inserts demo data on first launch so every later phase can be tested straight away.
 *
 * DEVELOPMENT ONLY: the demo passwords below must be removed before any real deployment.
 *
 * Demo logins (username / password):
 *   sysadmin  / System@123     (SYSTEM_ADMIN)
 *   admin     / Admin@123      (ELECTION_ADMIN)
 *   observer  / Observer@123   (OBSERVER)
 *   support   / Support@123    (SUPPORT_OFFICER)
 *   CS/0001/2024  / Voter@123  (VOTER, Computer Science)
 *   BBA/0001/2024 / Voter@123  (VOTER, Business Administration)
 */
class DatabaseSeeder(private val db: UniVoteDatabase) {

    suspend fun seedIfEmpty() {
        if (db.departmentDao().count() > 0) return

        // Hashing is CPU-heavy, so do it before opening the transaction.
        val users = withContext(Dispatchers.Default) { buildDemoUsers() }

        db.withTransaction {
            val deptDao = db.departmentDao()
            val cs = deptDao.insert(DepartmentEntity(name = "Computer Science", code = "CS"))
            val bba = deptDao.insert(DepartmentEntity(name = "Business Administration", code = "BBA"))
            val eng = deptDao.insert(DepartmentEntity(name = "Engineering", code = "ENG"))
            val edu = deptDao.insert(DepartmentEntity(name = "Education", code = "EDU"))
            val hs = deptDao.insert(DepartmentEntity(name = "Health Sciences", code = "HS"))

            db.voterDao().insertAll(
                listOf(
                    VoterEntity("CS/0001/2024", "Amina Wanjiru", cs, 2),
                    VoterEntity("CS/0002/2024", "Brian Otieno", cs, 2),
                    VoterEntity("CS/0003/2023", "Cynthia Achieng", cs, 3),
                    VoterEntity("BBA/0001/2024", "David Mutua", bba, 2),
                    VoterEntity("BBA/0002/2024", "Esther Njeri", bba, 2),
                    VoterEntity("BBA/0003/2022", "Felix Kiptoo", bba, 4),
                    VoterEntity("ENG/0001/2023", "Grace Atieno", eng, 3),
                    VoterEntity("ENG/0002/2024", "Hassan Abdi", eng, 2),
                    VoterEntity("EDU/0001/2024", "Irene Chebet", edu, 2),
                    VoterEntity("EDU/0002/2023", "James Mwangi", edu, 3),
                    VoterEntity("HS/0001/2024", "Karen Nyambura", hs, 2),
                    VoterEntity("HS/0002/2022", "Lawrence Omondi", hs, 4, isEligible = false)
                )
            )

            users.forEach { db.userDao().insert(it) }

            val partyDao = db.partyDao()
            val psf = partyDao.insert(
                PoliticalPartyEntity(name = "Progressive Students Front", acronym = "PSF", slogan = "Forward together")
            )
            val crm = partyDao.insert(
                PoliticalPartyEntity(name = "Campus Renewal Movement", acronym = "CRM", slogan = "A fresh start")
            )

            val now = System.currentTimeMillis()
            val electionId = db.electionDao().insert(
                ElectionEntity(
                    title = "Student Council Elections 2026",
                    description = "Annual election of the student council leadership and faculty representatives.",
                    startTime = now,
                    endTime = now + SEVEN_DAYS_MS,
                    status = ElectionStatus.SCHEDULED
                )
            )

            val posDao = db.positionDao()
            val president = posDao.insert(PositionEntity(electionId = electionId, title = "President"))
            val vicePresident = posDao.insert(PositionEntity(electionId = electionId, title = "Vice President"))
            val csRep = posDao.insert(
                PositionEntity(electionId = electionId, title = "Faculty Representative - Computer Science", departmentId = cs)
            )
            val bbaRep = posDao.insert(
                PositionEntity(electionId = electionId, title = "Faculty Representative - Business Administration", departmentId = bba)
            )

            val candDao = db.candidateDao()
            suspend fun candidate(
                positionId: Long,
                partyId: Long?,
                name: String,
                manifesto: String,
                status: CandidateStatus = CandidateStatus.APPROVED
            ) = candDao.insert(CandidateEntity(positionId = positionId, partyId = partyId, fullName = name, manifesto = manifesto, status = status))

            candidate(president, psf, "Daniel Kimani", "Better study spaces, extended library hours and transparent student funds.")
            candidate(president, crm, "Faith Wambui", "Affordable meals, stronger mental-health support and regular open forums.")
            candidate(president, null, "Samuel Odhiambo", "An independent voice: student welfare first, politics second.")
            candidate(vicePresident, psf, "Mercy Chepkemoi", "Digitise clearance and fee queries so students stop queuing.")
            candidate(vicePresident, crm, "Peter Karanja", "Safer night transport and better campus lighting.")
            candidate(csRep, psf, "Linet Awino", "More lab access and industry attachments for CS students.")
            candidate(csRep, crm, "Victor Mbugua", "Free coding bootcamps and a working peer-mentorship programme.")
            candidate(bbaRep, crm, "Naomi Waithera", "Entrepreneurship fair and guest lectures from local business leaders.")
            candidate(
                bbaRep, null, "Collins Rotich", "Pending verification by the election office.",
                status = CandidateStatus.PENDING
            )
        }
    }

    private fun buildDemoUsers(): List<UserEntity> {
        fun user(username: String, name: String, role: Role, password: String, regNumber: String? = null): UserEntity {
            val h = PasswordHasher.hash(password.toCharArray())
            return UserEntity(
                username = username,
                displayName = name,
                role = role,
                passwordHash = h.hash,
                salt = h.salt,
                voterRegNumber = regNumber
            )
        }
        return listOf(
            user("sysadmin", "System Administrator", Role.SYSTEM_ADMIN, "System@123"),
            user("admin", "Election Administrator", Role.ELECTION_ADMIN, "Admin@123"),
            user("observer", "Election Observer", Role.OBSERVER, "Observer@123"),
            user("support", "Support Officer", Role.SUPPORT_OFFICER, "Support@123"),
            user("CS/0001/2024", "Amina Wanjiru", Role.VOTER, "Voter@123", "CS/0001/2024"),
            user("BBA/0001/2024", "David Mutua", Role.VOTER, "Voter@123", "BBA/0001/2024")
        )
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
