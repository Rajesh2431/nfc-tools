package com.tapandgo.data.export

import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.ndef.NdefMessageCodec
import com.tapandgo.data.database.ContentKind
import com.tapandgo.data.database.ScanEntryEntity
import com.tapandgo.data.database.TemplateEntity
import com.tapandgo.data.database.TaskProfileEntity
import com.tapandgo.data.repository.TaskRepository
import com.tapandgo.data.repository.TemplateRepository
import com.tapandgo.domain.task.ConfirmationPolicy
import com.tapandgo.domain.task.TaskAction
import com.tapandgo.domain.task.TaskProfile
import com.tapandgo.domain.task.TaskTrigger
import com.tapandgo.domain.task.TaskValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned export/import format (spec 4.5). Import treats the file as
 * untrusted: size, structure, schema version and every item are validated
 * before anything is saved; invalid items are skipped and counted.
 */
@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val schemaVersion: Int = ExportCodec.SCHEMA_VERSION,
    val exportedAt: Long,
    val history: List<HistoryDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val profiles: List<ProfileDto> = emptyList(),
) {
    companion object {
        const val FORMAT = "tapandgo-export"
    }
}

@Serializable
data class HistoryDto(
    val createdAt: Long,
    val summary: String,
    val technologies: List<String>,
    val recordCount: Int,
    val contentKind: String,
    val result: String,
    val ndefType: String? = null,
    val uidHex: String? = null,
    val payloadHex: String? = null,
    val favourite: Boolean = false,
)

@Serializable
data class TemplateDto(
    val name: String,
    val description: String = "",
    val records: List<RecordDto>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ProfileDto(
    val name: String,
    val enabled: Boolean,
    val actions: List<TaskAction>,
    val policy: ConfirmationPolicy,
    val trigger: TaskTrigger? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val autoRun: Boolean = false,
)

/** Validated content ready to insert. */
data class ImportBundle(
    val history: List<ScanEntryEntity>,
    val templates: List<TemplateEntity>,
    val skippedHistory: Int,
    val skippedTemplates: Int,
    val profiles: List<TaskProfileEntity> = emptyList(),
    val skippedProfiles: Int = 0,
)

sealed interface ImportResult {
    data class Ok(val bundle: ImportBundle) : ImportResult
    data object TooLarge : ImportResult
    data object NotJson : ImportResult
    data object WrongFormat : ImportResult
    data class UnsupportedVersion(val version: Int) : ImportResult
    data object TooManyItems : ImportResult
}

object ExportCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    const val MAX_HISTORY = 10_000
    const val MAX_TEMPLATES = 1_000
    const val MAX_PROFILES = 500
    private const val MAX_PAYLOAD_HEX = 32 * 1024
    private val TECH = Regex("^[A-Za-z0-9]{1,32}$")
    private val CODE = Regex("^[A-Z_]{1,32}$")
    private val ONE_DAY = 24L * 60 * 60 * 1000

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(
        history: List<ScanEntryEntity>,
        templates: List<TemplateEntity>,
        now: Long,
        profiles: List<TaskProfileEntity> = emptyList(),
    ): String = json.encodeToString(
        ExportFile.serializer(),
        ExportFile(
            exportedAt = now,
            history = history.map { it.toDto() },
            templates = templates.mapNotNull { it.toDto() },
            profiles = profiles.mapNotNull { row -> TaskRepository.toModel(row)?.toDto() },
        ),
    )

    /**
     * @param keepPayload/keepUid current privacy settings; imported payloads
     * and UIDs are dropped when the user has not enabled storing them.
     */
    fun decode(text: String, now: Long, keepPayload: Boolean, keepUid: Boolean): ImportResult {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) return ImportResult.TooLarge
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return ImportResult.NotJson
        val obj = root as? kotlinx.serialization.json.JsonObject ?: return ImportResult.WrongFormat
        val format = (obj["format"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        if (format != ExportFile.FORMAT) return ImportResult.WrongFormat
        val version = (obj["schemaVersion"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            ?: return ImportResult.WrongFormat
        if (version != SCHEMA_VERSION) return ImportResult.UnsupportedVersion(version)
        val file = runCatching { json.decodeFromJsonElement(ExportFile.serializer(), root) }.getOrNull()
            ?: return ImportResult.WrongFormat
        if (file.history.size > MAX_HISTORY || file.templates.size > MAX_TEMPLATES || file.profiles.size > MAX_PROFILES) {
            return ImportResult.TooManyItems
        }

        val history = file.history.mapNotNull { it.toEntity(now, keepPayload, keepUid) }
        val templates = file.templates.mapNotNull { it.toEntity(now) }
        val profiles = file.profiles.mapNotNull { it.toEntity(now) }
        return ImportResult.Ok(
            ImportBundle(
                history, templates, file.history.size - history.size, file.templates.size - templates.size,
                profiles, file.profiles.size - profiles.size,
            ),
        )
    }

    private fun ScanEntryEntity.toDto() = HistoryDto(
        createdAt, summary, technologies.split(',').filter { it.isNotBlank() }, recordCount,
        contentKind, result, ndefType, uidHex, payloadHex, favourite,
    )

    private fun TemplateEntity.toDto(): TemplateDto? {
        val records = RecordJson.decode(recordsJson) ?: return null
        return TemplateDto(name, description, records.mapNotNull(RecordJson::toDto), createdAt, updatedAt)
    }

    private fun HistoryDto.toEntity(now: Long, keepPayload: Boolean, keepUid: Boolean): ScanEntryEntity? {
        if (createdAt <= 0 || createdAt > now + ONE_DAY) return null
        if (summary.length > 2_000 || recordCount !in 0..NdefMessageCodec.MAX_RECORDS) return null
        if (technologies.size > 16 || !technologies.all { TECH.matches(it) }) return null
        if (runCatching { ContentKind.valueOf(contentKind) }.isFailure || !CODE.matches(result)) return null
        if (ndefType != null && ndefType.length > 64) return null
        if (uidHex != null && (uidHex.length > 32 || uidHex.hexToBytesOrNull() == null)) return null
        if (payloadHex != null) {
            if (payloadHex.length > MAX_PAYLOAD_HEX) return null
            val bytes = payloadHex.hexToBytesOrNull() ?: return null
            if (runCatching { NdefMessageCodec.parse(bytes) }.isFailure) return null
        }
        return ScanEntryEntity(
            createdAt = createdAt,
            summary = summary,
            technologies = technologies.joinToString(","),
            recordCount = recordCount,
            contentKind = contentKind,
            result = result,
            ndefType = ndefType,
            uidHex = uidHex?.lowercase()?.takeIf { keepUid },
            payloadHex = payloadHex?.lowercase()?.takeIf { keepPayload },
            favourite = favourite,
        )
    }

    private fun TemplateDto.toEntity(now: Long): TemplateEntity? {
        val specs = RecordJson.validated(records) ?: return null
        if (TemplateRepository.validate(name, description, specs) != null) return null
        val created = createdAt.takeIf { it in 1..(now + ONE_DAY) } ?: now
        return TemplateEntity(
            name = name.trim(),
            description = description.trim(),
            recordsJson = RecordJson.encode(specs),
            createdAt = created,
            updatedAt = updatedAt.takeIf { it in created..(now + ONE_DAY) } ?: created,
        )
    }

    private fun TaskProfile.toDto() = ProfileDto(name, enabled, actions, policy, trigger, createdAt, updatedAt, autoRun)

    private fun ProfileDto.toEntity(now: Long): TaskProfileEntity? {
        val created = createdAt.takeIf { it in 1..(now + ONE_DAY) } ?: now
        val profile = TaskProfile(
            name = name.trim(), enabled = enabled, actions = actions, policy = policy, trigger = trigger, autoRun = autoRun,
            createdAt = created, updatedAt = updatedAt.takeIf { it in created..(now + ONE_DAY) } ?: created,
        )
        if (TaskValidator.validate(profile) != null) return null
        return TaskRepository.toEntity(profile)
    }
}
