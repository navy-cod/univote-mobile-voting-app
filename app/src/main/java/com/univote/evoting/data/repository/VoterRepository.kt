package com.univote.evoting.data.repository

import androidx.room.withTransaction
import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.VoterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ImportSummary(val added: Int, val skipped: Int, val errors: List<String>)

class VoterRepository(
    private val db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val actor: () -> String
) {
    private val voterDao = db.voterDao()
    private val deptDao = db.departmentDao()

    fun observeAll(): Flow<List<VoterEntity>> = voterDao.observeAll()

    suspend fun add(regNumber: String, fullName: String, departmentId: Long?, year: Int?, eligible: Boolean): ActionResult {
        val reg = regNumber.trim().uppercase()
        val name = fullName.trim()
        problem(reg, name, departmentId, year)?.let { return ActionResult.Failure(it) }
        if (voterDao.findByRegNumber(reg) != null) {
            return ActionResult.Failure("A voter with registration number $reg already exists.")
        }
        // departmentId and year were checked non-null by problem() above.
        voterDao.insert(VoterEntity(reg, name, departmentId!!, year!!, eligible))
        audit.log(actor(), "VOTER_ADDED", "reg=$reg")
        return ActionResult.Success
    }

    suspend fun update(regNumber: String, fullName: String, departmentId: Long?, year: Int?, eligible: Boolean): ActionResult {
        val name = fullName.trim()
        problem(regNumber, name, departmentId, year)?.let { return ActionResult.Failure(it) }
        val existing = voterDao.findByRegNumber(regNumber) ?: return ActionResult.Failure("Voter not found.")
        voterDao.update(
            existing.copy(fullName = name, departmentId = departmentId!!, yearOfStudy = year!!, isEligible = eligible)
        )
        audit.log(actor(), "VOTER_UPDATED", "reg=$regNumber eligible=$eligible")
        return ActionResult.Success
    }

    /** Voters are never deleted (their history must stay auditable); they are marked ineligible instead. */
    suspend fun setEligible(regNumber: String, eligible: Boolean): ActionResult {
        val existing = voterDao.findByRegNumber(regNumber) ?: return ActionResult.Failure("Voter not found.")
        voterDao.update(existing.copy(isEligible = eligible))
        audit.log(actor(), "VOTER_ELIGIBILITY_CHANGED", "reg=$regNumber eligible=$eligible")
        return ActionResult.Success
    }

    /** CSV columns: regNumber,fullName,departmentCode,yearOfStudy (header row optional). */
    suspend fun importCsv(text: String): ImportSummary = withContext(Dispatchers.Default) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var added = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        db.withTransaction {
            lines.forEachIndexed { index, line ->
                if (index == 0 && line.lowercase().startsWith("regnumber")) return@forEachIndexed
                val lineNo = index + 1
                val parts = line.split(",").map { it.trim() }
                if (parts.size != 4) {
                    errors.add("Line $lineNo: expected 4 columns.")
                    return@forEachIndexed
                }
                val reg = parts[0].uppercase()
                val name = parts[1]
                val year = parts[3].toIntOrNull()
                val dept = deptDao.findByCode(parts[2])
                if (reg.isBlank() || name.isBlank()) {
                    errors.add("Line $lineNo: registration number and name are required.")
                } else if (year == null || year !in 1..6) {
                    errors.add("Line $lineNo: year of study must be 1-6.")
                } else if (dept == null) {
                    errors.add("Line $lineNo: unknown department code '${parts[2]}'.")
                } else if (voterDao.findByRegNumber(reg) != null) {
                    skipped++
                } else {
                    voterDao.insert(VoterEntity(reg, name, dept.id, year))
                    added++
                }
            }
        }
        audit.log(actor(), "VOTERS_IMPORTED", "added=$added skipped=$skipped errors=${errors.size}")
        ImportSummary(added, skipped, errors.take(20))
    }

    private fun problem(reg: String, name: String, departmentId: Long?, year: Int?): String? = when {
        reg.isBlank() -> "Enter the registration number."
        name.isBlank() -> "Enter the student's full name."
        departmentId == null -> "Choose a department."
        year == null || year !in 1..6 -> "Year of study must be between 1 and 6."
        else -> null
    }
}