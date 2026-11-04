package dev.nfcstudio.app.data.repository

import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.RecordValidator
import dev.nfcstudio.app.data.database.TemplateDao
import dev.nfcstudio.app.data.database.TemplateEntity
import dev.nfcstudio.app.data.export.RecordJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A template with its records already validated. Invalid stored rows surface as [records] == null. */
data class Template(
    val id: Long,
    val name: String,
    val description: String,
    val records: List<RecordSpec>?,
    val updatedAt: Long,
)

class TemplateRepository(
    private val dao: TemplateDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Problem { NAME_EMPTY, NAME_TOO_LONG, DESCRIPTION_TOO_LONG, NO_RECORDS, INVALID_RECORD }

    fun observeAll(): Flow<List<Template>> = dao.observeAll().map { rows -> rows.map(::toModel) }

    /** @return null on success, else the validation problem. Nothing is saved on failure. */
    suspend fun save(name: String, description: String, records: List<RecordSpec>): Problem? {
        validate(name, description, records)?.let { return it }
        val t = now()
        dao.insert(TemplateEntity(name = name.trim(), description = description.trim(), recordsJson = RecordJson.encode(records), createdAt = t, updatedAt = t))
        return null
    }

    suspend fun rename(id: Long, name: String, description: String): Problem? {
        val row = dao.byId(id) ?: return Problem.NO_RECORDS
        validateMeta(name, description)?.let { return it }
        dao.update(row.copy(name = name.trim(), description = description.trim(), updatedAt = now()))
        return null
    }

    suspend fun duplicate(id: Long, copySuffix: String) {
        val row = dao.byId(id) ?: return
        val t = now()
        dao.insert(row.copy(id = 0, name = (row.name + " " + copySuffix).take(MAX_NAME), createdAt = t, updatedAt = t))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        const val MAX_NAME = 80
        const val MAX_DESCRIPTION = 500

        fun validateMeta(name: String, description: String): Problem? = when {
            name.isBlank() -> Problem.NAME_EMPTY
            name.trim().length > MAX_NAME -> Problem.NAME_TOO_LONG
            description.trim().length > MAX_DESCRIPTION -> Problem.DESCRIPTION_TOO_LONG
            else -> null
        }

        fun validate(name: String, description: String, records: List<RecordSpec>): Problem? {
            validateMeta(name, description)?.let { return it }
            if (records.isEmpty() || records.size > RecordJson.MAX_RECORDS) return Problem.NO_RECORDS
            if (records.any { it == RecordSpec.Empty || RecordValidator.validate(it) != null }) return Problem.INVALID_RECORD
            return null
        }

        fun toModel(row: TemplateEntity) = Template(row.id, row.name, row.description, RecordJson.decode(row.recordsJson), row.updatedAt)
    }
}
