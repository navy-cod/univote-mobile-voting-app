package com.univote.evoting.data.repository

import com.univote.evoting.data.audit.AuditLogger
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.DepartmentEntity
import kotlinx.coroutines.flow.Flow

class DepartmentRepository(
    db: UniVoteDatabase,
    private val audit: AuditLogger,
    private val actor: () -> String
) {
    private val dao = db.departmentDao()
    private val voterDao = db.voterDao()
    private val positionDao = db.positionDao()

    fun observeAll(): Flow<List<DepartmentEntity>> = dao.observeAll()

    suspend fun add(name: String, code: String): ActionResult {
        val n = name.trim()
        val c = code.trim().uppercase()
        problem(n, c)?.let { return ActionResult.Failure(it) }
        if (dao.findByCode(c) != null) return ActionResult.Failure("A department with code $c already exists.")
        dao.insert(DepartmentEntity(name = n, code = c))
        audit.log(actor(), "DEPARTMENT_ADDED", "code=$c name=$n")
        return ActionResult.Success
    }

    suspend fun update(id: Long, name: String, code: String): ActionResult {
        val n = name.trim()
        val c = code.trim().uppercase()
        problem(n, c)?.let { return ActionResult.Failure(it) }
        val clash = dao.findByCode(c)
        if (clash != null && clash.id != id) return ActionResult.Failure("A department with code $c already exists.")
        dao.update(DepartmentEntity(id = id, name = n, code = c))
        audit.log(actor(), "DEPARTMENT_UPDATED", "id=$id code=$c name=$n")
        return ActionResult.Success
    }

    suspend fun delete(department: DepartmentEntity): ActionResult {
        if (voterDao.countByDepartment(department.id) > 0 || positionDao.countByDepartment(department.id) > 0) {
            return ActionResult.Failure("${department.name} still has voters or positions, so it cannot be deleted.")
        }
        dao.delete(department)
        audit.log(actor(), "DEPARTMENT_DELETED", "code=${department.code}")
        return ActionResult.Success
    }

    private fun problem(name: String, code: String): String? = when {
        name.isBlank() -> "Enter the department name."
        code.isBlank() -> "Enter a short code."
        code.length > 10 -> "The code must be 10 characters or fewer."
        else -> null
    }
}