package com.tapandgo.data.library

import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.ndef.DecodedRecord
import com.tapandgo.core.ndef.NdefMessageCodec
import com.tapandgo.core.ndef.NdefParser
import com.tapandgo.core.nfc.NdefContent
import com.tapandgo.core.nfc.TagSnapshot
import com.tapandgo.data.database.SavedTagEntity

/** Icons a saved tag can carry (stored by name). */
enum class LibraryIcon { TAG, HOME, WORK, CAR, KEY, WIFI, LINK, CONTACT, STAR }

enum class LibrarySort { UPDATED, NAME, SCANNED }

data class LibraryFilter(
    val query: String = "",
    val folder: String? = null,
    val favouritesOnly: Boolean = false,
    val sort: LibrarySort = LibrarySort.UPDATED,
)

/** Editable metadata; validated before saving. */
data class LibraryMeta(
    val name: String,
    val description: String = "",
    val notes: String = "",
    val icon: LibraryIcon = LibraryIcon.TAG,
    val folder: String = "",
    val labels: List<String> = emptyList(),
) {
    enum class Problem { NAME_EMPTY, NAME_TOO_LONG, TEXT_TOO_LONG, LABELS_INVALID }

    fun validate(): Problem? = when {
        name.isBlank() -> Problem.NAME_EMPTY
        name.trim().length > 80 -> Problem.NAME_TOO_LONG
        description.length > 500 || notes.length > 4000 || folder.trim().length > 60 -> Problem.TEXT_TOO_LONG
        labels.size > 20 || labels.any { it.isBlank() || it.length > 30 || ',' in it } -> Problem.LABELS_INVALID
        else -> null
    }
}

/** Field-level difference between two saved tags (Compare screen). */
data class Difference(val field: Field, val left: String?, val right: String?) {
    enum class Field { TECHNOLOGIES, CHIP, NDEF_TYPE, CAPACITY, WRITABLE, RECORD_COUNT, RECORD, UID }
}

object Library {

    fun entityFrom(snapshot: TagSnapshot, meta: LibraryMeta, now: Long): SavedTagEntity {
        val ndef = snapshot.ndef
        return SavedTagEntity(
            name = meta.name.trim(),
            description = meta.description.trim(),
            notes = meta.notes.trim(),
            icon = meta.icon.name,
            folder = meta.folder.trim(),
            labels = meta.labels.map { it.trim() }.distinct().joinToString(","),
            uidHex = snapshot.uidHex,
            technologies = snapshot.technologies.joinToString(","),
            chipName = snapshot.chip?.let { listOfNotNull(it.vendor, it.name).joinToString(" ") },
            ndefType = ndef?.ndefType,
            capacityBytes = ndef?.maxCapacityBytes,
            writable = ndef?.isWritable,
            payloadHex = (ndef?.content as? NdefContent.Records)?.rawMessageHex,
            scannedAt = now,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun withMeta(e: SavedTagEntity, meta: LibraryMeta, now: Long) = e.copy(
        name = meta.name.trim(), description = meta.description.trim(), notes = meta.notes.trim(), icon = meta.icon.name,
        folder = meta.folder.trim(), labels = meta.labels.map { it.trim() }.distinct().joinToString(","), updatedAt = now,
    )

    fun metaOf(e: SavedTagEntity) = LibraryMeta(
        e.name, e.description, e.notes,
        LibraryIcon.entries.firstOrNull { it.name == e.icon } ?: LibraryIcon.TAG,
        e.folder, e.labels.split(',').filter { it.isNotBlank() },
    )

    /** Decoded records of a saved message; empty when none or when it no longer parses. */
    fun records(e: SavedTagEntity): List<DecodedRecord> {
        val bytes = e.payloadHex?.hexToBytesOrNull() ?: return emptyList()
        return runCatching { NdefParser.decodeAll(NdefMessageCodec.parse(bytes)) }.getOrDefault(emptyList())
    }

    fun compare(a: SavedTagEntity, b: SavedTagEntity): List<Difference> {
        val out = mutableListOf<Difference>()
        fun cmp(f: Difference.Field, x: Any?, y: Any?) { if (x != y) out += Difference(f, x?.toString(), y?.toString()) }
        cmp(Difference.Field.UID, a.uidHex, b.uidHex)
        cmp(Difference.Field.TECHNOLOGIES, a.technologies, b.technologies)
        cmp(Difference.Field.CHIP, a.chipName, b.chipName)
        cmp(Difference.Field.NDEF_TYPE, a.ndefType, b.ndefType)
        cmp(Difference.Field.CAPACITY, a.capacityBytes, b.capacityBytes)
        cmp(Difference.Field.WRITABLE, a.writable, b.writable)
        val ra = records(a)
        val rb = records(b)
        cmp(Difference.Field.RECORD_COUNT, ra.size, rb.size)
        for (i in 0 until maxOf(ra.size, rb.size)) {
            val x = ra.getOrNull(i)?.let { "${it.kind}: ${it.summarize(80)}" }
            val y = rb.getOrNull(i)?.let { "${it.kind}: ${it.summarize(80)}" }
            if (x != y) out += Difference(Difference.Field.RECORD, x, y)
        }
        return out
    }

    /** CSV (RFC 4180 quoting) for spreadsheets; payload as hex. */
    fun toCsv(tags: List<SavedTagEntity>): String = buildString {
        append("name,folder,labels,favourite,technologies,chip,ndef_type,capacity,writable,records,scanned_at,payload_hex\r\n")
        tags.forEach { t ->
            listOf(
                t.name, t.folder, t.labels, t.favourite.toString(), t.technologies, t.chipName.orEmpty(), t.ndefType.orEmpty(),
                t.capacityBytes?.toString().orEmpty(), t.writable?.toString().orEmpty(),
                records(t).joinToString(" | ") { it.summarize(120) }, t.scannedAt.toString(), t.payloadHex.orEmpty(),
            ).joinToString(",") { csv(it) }.let { append(it).append("\r\n") }
        }
    }

    /** Spreadsheet formula injection guard: prefix =,+,-,@ with an apostrophe. */
    internal fun csv(v: String): String {
        val safe = if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun toText(tags: List<SavedTagEntity>): String = buildString {
        tags.forEach { t ->
            append("# ").append(t.name).append('\n')
            if (t.folder.isNotBlank()) append("Folder: ").append(t.folder).append('\n')
            if (t.labels.isNotBlank()) append("Labels: ").append(t.labels.replace(",", ", ")).append('\n')
            append("Technologies: ").append(t.technologies.replace(",", ", ")).append('\n')
            t.chipName?.let { append("Chip: ").append(it).append('\n') }
            records(t).forEachIndexed { i, r -> append("Record ").append(i + 1).append(" (").append(r.kind).append("): ").append(r.summarize(200)).append('\n') }
            if (t.notes.isNotBlank()) append("Notes: ").append(t.notes).append('\n')
            append('\n')
        }
    }
}
