package com.tapandgo.core.ndef

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NdefUriCodecTest {

    /** NFC Forum URI RTD 1.0, table 3 (also android.nfc.NdefRecord.URI_PREFIX_MAP). */
    private val normative = mapOf(
        0x00 to "", 0x01 to "http://www.", 0x02 to "https://www.", 0x03 to "http://", 0x04 to "https://",
        0x05 to "tel:", 0x06 to "mailto:", 0x07 to "ftp://anonymous:anonymous@", 0x08 to "ftp://ftp.",
        0x09 to "ftps://", 0x0A to "sftp://", 0x0B to "smb://", 0x0C to "nfs://", 0x0D to "ftp://",
        0x0E to "dav://", 0x0F to "news:", 0x10 to "telnet://", 0x11 to "imap:", 0x12 to "rtsp://",
        0x13 to "urn:", 0x14 to "pop:", 0x15 to "sip:", 0x16 to "sips:", 0x17 to "tftp:",
        0x18 to "btspp://", 0x19 to "btl2cap://", 0x1A to "btgoep://", 0x1B to "tcpobex://",
        0x1C to "irdaobex://", 0x1D to "file://", 0x1E to "urn:epc:id:", 0x1F to "urn:epc:tag:",
        0x20 to "urn:epc:pat:", 0x21 to "urn:epc:raw:", 0x22 to "urn:epc:", 0x23 to "urn:nfc:",
    )

    @Test
    fun `prefix table matches the NFC Forum specification exactly`() {
        assertEquals(normative.size, NdefUriCodec.prefixes.size)
        normative.forEach { (code, prefix) -> assertEquals("code $code", prefix, NdefUriCodec.prefixes[code]) }
    }

    @Test
    fun `every prefix code decodes to its normative prefix`() {
        normative.forEach { (code, prefix) ->
            assertEquals(prefix + "x", NdefUriCodec.decode(byteArrayOf(code.toByte(), 'x'.code.toByte())))
        }
    }

    @Test
    fun `https url round trip with www prefix`() {
        val uri = "https://www.example.com/page"
        val payload = NdefUriCodec.encode(uri)
        assertEquals(2, payload[0].toInt())
        assertEquals(uri, NdefUriCodec.decode(payload))
    }

    @Test
    fun `plain http url uses shorter prefix`() {
        val payload = NdefUriCodec.encode("http://example.org")
        assertEquals(3, payload[0].toInt())
        assertEquals("http://example.org", NdefUriCodec.decode(payload))
    }

    @Test
    fun `epc urns use the longest specific prefix`() {
        assertEquals(0x1E, NdefUriCodec.encode("urn:epc:id:sgtin:1").first().toInt())
        assertEquals(0x22, NdefUriCodec.encode("urn:epc:other").first().toInt())
        assertEquals(0x13, NdefUriCodec.encode("urn:uuid:123").first().toInt())
    }

    @Test
    fun `unknown scheme stores full uri`() {
        val uri = "magnet:?xt=urn:sha1:123"
        val payload = NdefUriCodec.encode(uri)
        assertEquals(0, payload[0].toInt())
        assertEquals(uri, NdefUriCodec.decode(payload))
    }

    @Test
    fun `utf-8 remainder round trips`() {
        val uri = "https://example.com/straße"
        assertEquals(uri, NdefUriCodec.decode(NdefUriCodec.encode(uri)))
    }

    @Test
    fun `decode rejects reserved prefix codes and empty payload`() {
        assertThrows(NdefParseException::class.java) { NdefUriCodec.decode(byteArrayOf(0x24, 'x'.code.toByte())) }
        assertThrows(NdefParseException::class.java) { NdefUriCodec.decode(byteArrayOf(0xFF.toByte())) }
        assertThrows(NdefParseException::class.java) { NdefUriCodec.decode(ByteArray(0)) }
    }
}
