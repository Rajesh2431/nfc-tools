package dev.nfcstudio.app.testing

import dev.nfcstudio.app.core.ndef.NdefParser
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.toRawRecord
import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NdefSnapshot
import dev.nfcstudio.app.core.nfc.TagSnapshot

fun ndefTag(
    content: NdefContent = NdefContent.Empty,
    capacity: Int? = 137,
    writable: Boolean? = true,
    canLock: Boolean? = true,
) = TagSnapshot(
    uidHex = "04a1b2c3",
    technologies = listOf("NfcA", "MifareUltralight", "Ndef"),
    ndef = NdefSnapshot("org.nfcforum.ndef.type2", capacity, writable, canLock, content, fromCache = false),
    formatable = false,
)

fun formatableTag() = TagSnapshot("04a1", listOf("NfcA", "NdefFormatable"), ndef = null, formatable = true)

fun plainTag() = TagSnapshot(null, listOf("NfcA"), ndef = null, formatable = false)

fun recordsContent(vararg specs: RecordSpec) = NdefContent.Records(
    records = NdefParser.decodeAll(specs.map { it.toRawRecord() }),
    rawMessageHex = "",
    sizeBytes = 0,
)
