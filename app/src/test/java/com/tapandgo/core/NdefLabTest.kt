package com.tapandgo.core

import com.tapandgo.core.common.toHex
import com.tapandgo.core.ndef.NdefLab
import com.tapandgo.core.ndef.NdefParser
import com.tapandgo.core.ndef.RecordProblem
import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.ndef.RecordValidator
import com.tapandgo.core.ndef.toRawSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefLabTest {
    // Well-known Text "hi" (en): D1 01 05 54 02 65 6E 68 69
    private val textHex = "d101055402656e6869"

    @Test
    fun `decodes plain message and TLV-wrapped memory`() {
        val plain = NdefLab.decodeHex(textHex) as NdefLab.Decoded.Ok
        assertEquals(1, plain.records.size)
        assertEquals(false, plain.unwrappedFromTlv)
        val tlv = NdefLab.decodeHex("00 03 09 $textHex FE 00") as NdefLab.Decoded.Ok
        assertTrue(tlv.unwrappedFromTlv)
        assertEquals(plain.records, tlv.records)
    }

    @Test
    fun `rejects bad hex and malformed ndef`() {
        assertTrue(NdefLab.decodeHex("zz") is NdefLab.Decoded.Error)
        assertTrue(NdefLab.decodeHex("abc") is NdefLab.Decoded.Error)
        assertTrue(NdefLab.decodeHex("d10105") is NdefLab.Decoded.Error)
        assertNull(NdefLab.unwrapTlv(byteArrayOf(0x03, 0x10, 0x01)))
    }

    @Test
    fun `raw record round trip is byte exact`() {
        val parsed = (NdefLab.decodeHex(textHex) as NdefLab.Decoded.Ok).records
        val specs = parsed.map { it.toRawSpec() }
        val (bytes, bad) = NdefLab.encode(specs)
        assertNull(bad)
        assertEquals(textHex, bytes!!.toHex().lowercase())
    }

    @Test
    fun `raw validation follows NDEF structure rules`() {
        assertNull(RecordValidator.validate(RecordSpec.Raw(NdefParser.TNF_EMPTY, "", "", "")))
        assertEquals(RecordProblem.RAW_INVALID, RecordValidator.validate(RecordSpec.Raw(NdefParser.TNF_EMPTY, "54", "", "")))
        assertEquals(RecordProblem.RAW_INVALID, RecordValidator.validate(RecordSpec.Raw(NdefParser.TNF_UNKNOWN, "54", "", "00")))
        assertEquals(RecordProblem.RAW_INVALID, RecordValidator.validate(RecordSpec.Raw(NdefParser.TNF_MIME_MEDIA, "", "", "00")))
        assertEquals(RecordProblem.RAW_INVALID, RecordValidator.validate(RecordSpec.Raw(6, "54", "", "")))
        assertEquals(RecordProblem.RAW_INVALID, RecordValidator.validate(RecordSpec.Raw(1, "5", "", "")))
        assertNull(RecordValidator.validate(RecordSpec.Raw(NdefParser.TNF_UNKNOWN, "", "01", "ff")))
        assertEquals(-1, NdefLab.encode(emptyList()).second)
    }

    @Test
    fun `hexdump formats offset hex and ascii`() {
        val dump = NdefLab.hexdump("Hello, NFC world!".toByteArray())
        assertTrue(dump.startsWith("0000  48 65 6C 6C 6F"))
        assertTrue(dump.contains("Hello, NFC world"))
        assertTrue(dump.lines()[1].startsWith("0010  21"))
        assertEquals("746578742f706c61696e", NdefLab.asciiToHex("text/plain"))
        assertNull(NdefLab.asciiToHex("é"))
    }
}
