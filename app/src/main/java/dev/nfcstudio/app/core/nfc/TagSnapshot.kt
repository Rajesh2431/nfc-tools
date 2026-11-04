package dev.nfcstudio.app.core.nfc

import dev.nfcstudio.app.core.ndef.DecodedRecord

/**
 * Immutable description of a discovered tag. Unknown values are null, never
 * guessed (spec § 8: represent unknown as unknown).
 */
data class TagSnapshot(
    /** Hex UID exactly as Android exposes it; null when absent. */
    val uidHex: String?,
    /** Simple technology names, e.g. "NfcA", "Ndef". */
    val technologies: List<String>,
    val ndef: NdefSnapshot?,
    val formatable: Boolean,
    /** Per-technology metadata for the advanced inspector. */
    val techDetails: List<TechDetail> = emptyList(),
    /** Chip identification (Scan only); null when not attempted or unknown. */
    val chip: ChipInfo? = null,
) {
    val supportsIsoDep: Boolean get() = "IsoDep" in technologies
}

data class NdefSnapshot(
    /** Platform NDEF type string, e.g. "org.nfcforum.ndef.type2". */
    val ndefType: String?,
    /** null when the platform does not expose a meaningful value. */
    val maxCapacityBytes: Int?,
    /** Platform fact from Ndef.isWritable(); null when unknown. */
    val isWritable: Boolean?,
    /** Platform fact from Ndef.canMakeReadOnly(); null when unknown. */
    val canMakeReadOnly: Boolean?,
    val content: NdefContent,
    /** True when the live read failed and the discovery-time cache was used. */
    val fromCache: Boolean,
)

/** What the NDEF area holds. Anything but [Empty] requires overwrite confirmation. */
sealed interface NdefContent {
    data object Empty : NdefContent

    data class Records(
        val records: List<DecodedRecord>,
        /** Encoded NDEF message bytes as hex (for raw view and optional history). */
        val rawMessageHex: String,
        val sizeBytes: Int,
    ) : NdefContent

    /** The tag holds data the platform could not parse as NDEF. */
    data object Malformed : NdefContent

    /** Content could not be read (I/O failure and no cached message). */
    data object Unreadable : NdefContent
}

/** One technology's metadata; labels are protocol terms and not localized. */
data class TechDetail(val technology: String, val properties: List<Pair<String, String>>)

/** Localizable description of what a tag currently holds (for confirmations). */
data class ContentSummary(val kind: Kind, val recordCount: Int = 0, val preview: String? = null) {
    enum class Kind { EMPTY, RECORDS, MALFORMED, UNREADABLE }

    companion object {
        fun of(content: NdefContent, maxPreview: Int = 40): ContentSummary = when (content) {
            NdefContent.Empty -> ContentSummary(Kind.EMPTY)
            NdefContent.Malformed -> ContentSummary(Kind.MALFORMED)
            NdefContent.Unreadable -> ContentSummary(Kind.UNREADABLE)
            is NdefContent.Records -> ContentSummary(
                Kind.RECORDS,
                recordCount = content.records.size,
                preview = content.records.firstOrNull()?.summarize(maxPreview),
            )
        }
    }
}
