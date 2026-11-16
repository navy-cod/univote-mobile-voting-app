package com.univote.evoting.data.repository

import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.PoliticalPartyEntity
import kotlinx.coroutines.flow.Flow

class PartyRepository(
    db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val actor: () -> String
) {
    private val dao = db.partyDao()
    private val candidateDao = db.candidateDao()

    fun observeAll(): Flow<List<PoliticalPartyEntity>> = dao.observeAll()

    suspend fun add(name: String, acronym: String, slogan: String): ActionResult {
        val n = name.trim()
        val a = acronym.trim().uppercase()
        problem(n, a)?.let { return ActionResult.Failure(it) }
        if (dao.findByAcronym(a) != null) return ActionResult.Failure("A party with acronym $a already exists.")
        dao.insert(PoliticalPartyEntity(name = n, acronym = a, slogan = slogan.trim().ifBlank { null }))
        audit.log(actor(), "PARTY_ADDED", "acronym=$a name=$n")
        return ActionResult.Success
    }

    suspend fun update(id: Long, name: String, acronym: String, slogan: String): ActionResult {
        val n = name.trim()
        val a = acronym.trim().uppercase()
        problem(n, a)?.let { return ActionResult.Failure(it) }
        val clash = dao.findByAcronym(a)
        if (clash != null && clash.id != id) return ActionResult.Failure("A party with acronym $a already exists.")
        dao.update(PoliticalPartyEntity(id = id, name = n, acronym = a, slogan = slogan.trim().ifBlank { null }))
        audit.log(actor(), "PARTY_UPDATED", "id=$id acronym=$a")
        return ActionResult.Success
    }

    suspend fun delete(party: PoliticalPartyEntity): ActionResult {
        if (candidateDao.countByParty(party.id) > 0) {
            return ActionResult.Failure("${party.name} has candidates, so it cannot be deleted.")
        }
        dao.delete(party)
        audit.log(actor(), "PARTY_DELETED", "acronym=${party.acronym}")
        return ActionResult.Success
    }

    private fun problem(name: String, acronym: String): String? = when {
        name.isBlank() -> "Enter the party name."
        acronym.isBlank() -> "Enter the party acronym."
        acronym.length > 10 -> "The acronym must be 10 characters or fewer."
        else -> null
    }
}