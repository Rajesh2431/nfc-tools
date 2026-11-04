package com.tapandgo.data.export

import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.ndef.RecordValidator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Stable JSON form of a [RecordSpec] for templates and exports. Data only — never code. */
@Serializable
sealed class RecordDto {
    @Serializable
    @SerialName("text")
    data class Text(val text: String, val language: String = "en", val utf16: Boolean = false) : RecordDto()

    @Serializable
    @SerialName("uri")
    data class Uri(val uri: String) : RecordDto()

    @Serializable
    @SerialName("mime")
    data class Mime(val mimeType: String, val payload: String) : RecordDto()

    @Serializable
    @SerialName("mime_binary")
    data class MimeBinary(val mimeType: String, val payloadHex: String) : RecordDto()

    @Serializable
    @SerialName("external")
    data class External(val externalType: String, val payloadHex: String) : RecordDto()
}

object RecordJson {
    const val MAX_RECORDS = 32

    /** Strict: unknown keys or types are rejected, not ignored. */
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun toDto(spec: RecordSpec): RecordDto? = when (spec) {
        is RecordSpec.Text -> RecordDto.Text(spec.text, spec.language, spec.encodeAsUtf16)
        is RecordSpec.Uri -> RecordDto.Uri(spec.uri)
        is RecordSpec.Mime -> RecordDto.Mime(spec.mimeType, spec.payload)
        is RecordSpec.MimeBinary -> RecordDto.MimeBinary(spec.mimeType, spec.payloadHex)
        is RecordSpec.External -> RecordDto.External(spec.type, spec.payloadHex)
        RecordSpec.Empty -> null
    }

    fun fromDto(dto: RecordDto): RecordSpec = when (dto) {
        is RecordDto.Text -> RecordSpec.Text(dto.text, dto.language, dto.utf16)
        is RecordDto.Uri -> RecordSpec.Uri(dto.uri)
        is RecordDto.Mime -> RecordSpec.Mime(dto.mimeType, dto.payload)
        is RecordDto.MimeBinary -> RecordSpec.MimeBinary(dto.mimeType, dto.payloadHex.lowercase())
        is RecordDto.External -> RecordSpec.External(dto.externalType, dto.payloadHex.lowercase())
    }

    fun encode(specs: List<RecordSpec>): String =
        json.encodeToString(ListSerializer(RecordDto.serializer()), specs.mapNotNull(::toDto))

    /** @return validated specs, or null when the JSON or any record is invalid. */
    fun decode(text: String): List<RecordSpec>? {
        val dtos = runCatching { json.decodeFromString(ListSerializer(RecordDto.serializer()), text) }.getOrNull()
            ?: return null
        return validated(dtos)
    }

    fun validated(dtos: List<RecordDto>): List<RecordSpec>? {
        if (dtos.isEmpty() || dtos.size > MAX_RECORDS) return null
        val specs = dtos.map(::fromDto)
        return specs.takeIf { list -> list.all { RecordValidator.validate(it) == null } }
    }
}
