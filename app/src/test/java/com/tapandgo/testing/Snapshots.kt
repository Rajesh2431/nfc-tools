package com.tapandgo.testing

import com.tapandgo.core.ndef.NdefParser
import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.ndef.toRawRecord
import com.tapandgo.core.nfc.NdefContent
import com.tapandgo.core.nfc.NdefSnapshot
import com.tapandgo.core.nfc.TagSnapshot

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
