package com.tapandgo.core.ndef

import com.tapandgo.core.ndef.NdefParser.RawRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefParserTest {

    @Test
    fun `decodes well-known text record`() {
        val decoded = NdefParser.decode(RecordSpec.Text("hello", "en").toRawRecord())
        assertEquals(RecordKind.TEXT, decoded.kind)
        assertEquals("hello", decoded.text)
        assertEquals("en", decoded.language)
    }

    @Test
    fun `decodes well-known uri record`() {
        val decoded = NdefParser.decode(RecordSpec.Uri("https://example.com").toRawRecord())
        assertEquals(RecordKind.URI, decoded.kind)
        assertEquals("https://example.com", decoded.uri)
    }

    @Test
    fun `malformed text payload becomes unknown with note`() {
        val decoded = NdefParser.decode(RawRecord(1, byteArrayOf(0x54), byteArrayOf(0x7F, 0x01)))
        assertEquals(RecordKind.UNKNOWN, decoded.kind)
        assertNotNull(decoded.note)
    }

    @Test
    fun `reserved uri prefix becomes unknown with note`() {
        val decoded = NdefParser.decode(RawRecord(1, byteArrayOf(0x55), byteArrayOf(0x30, 0x41)))
        assertEquals(RecordKind.UNKNOWN, decoded.kind)
        assertNotNull(decoded.note)
    }

    @Test
    fun `mime media record keeps type, size and ascii preview`() {
        val decoded = NdefParser.decode(RecordSpec.Mime("application/json", """{"a":1}""").toRawRecord())
        assertEquals(RecordKind.MIME, decoded.kind)
        assertEquals("application/json", decoded.mimeType)
        assertEquals(7, decoded.payloadSize)
        assertEquals("""{"a":1}""", decoded.asciiPreview)
    }

    @Test
    fun `ascii preview masks control and non-ascii bytes`() {
        val decoded = NdefParser.decode(RawRecord(2, "a/b".toByteArray(), byteArrayOf(0x41, 0x07, 0xC3.toByte(), 0x42)))
        assertEquals("A..B", decoded.asciiPreview)
    }

    @Test
    fun `empty tnf decodes to empty kind`() {
        assertEquals(RecordKind.EMPTY, NdefParser.decode(RecordSpec.Empty.toRawRecord()).kind)
    }

    @Test
    fun `unknown and reserved tnf stay unknown without crashing`() {
        val unknown = NdefParser.decode(RawRecord(5, byteArrayOf(1, 2, 3), ByteArray(4) { 0x0F }))
        assertEquals(RecordKind.UNKNOWN, unknown.kind)
        assertEquals(4, unknown.payloadSize)
        assertNotNull(unknown.rawHex)
        assertEquals(RecordKind.UNKNOWN, NdefParser.decode(RawRecord(7, ByteArray(0), ByteArray(0))).kind)
    }

    @Test
    fun `absolute uri tnf exposes uri from type`() {
        val decoded = NdefParser.decode(RawRecord(3, "https://example.org".toByteArray(), byteArrayOf(1)))
        assertEquals(RecordKind.URI, decoded.kind)
        assertEquals("https://example.org", decoded.uri)
    }

    @Test
    fun `smart poster exposes nested uri and title`() {
        val nested = NdefMessageCodec.encode(
            listOf(RecordSpec.Uri("https://example.com").toRawRecord(), RecordSpec.Text("Example", "en").toRawRecord()),
        )
        val decoded = NdefParser.decode(RawRecord(1, NdefParser.SMART_POSTER_TYPE, nested))
        assertEquals(RecordKind.SMART_POSTER, decoded.kind)
        assertEquals("https://example.com", decoded.uri)
        assertEquals("Example", decoded.text)
    }

    @Test
    fun `smart poster with garbage payload is reported not thrown`() {
        val decoded = NdefParser.decode(RawRecord(1, NdefParser.SMART_POSTER_TYPE, byteArrayOf(1, 2, 3)))
        assertEquals(RecordKind.UNKNOWN, decoded.kind)
        assertNotNull(decoded.note)
    }

    @Test
    fun `huge payload hex preview is capped`() {
        val decoded = NdefParser.decode(RawRecord(2, "a/b".toByteArray(), ByteArray(10_000)))
        assertEquals(NdefParser.MAX_HEX_BYTES * 2, decoded.rawHex!!.length)
        assertTrue(decoded.rawHexTruncated)
    }

    @Test
    fun `record summarize truncates long content`() {
        val decoded = NdefParser.decode(RecordSpec.Uri("https://very-long-host.example.com/" + "x".repeat(60)).toRawRecord())
        assertEquals(20, decoded.summarize(20).length)
    }
}
