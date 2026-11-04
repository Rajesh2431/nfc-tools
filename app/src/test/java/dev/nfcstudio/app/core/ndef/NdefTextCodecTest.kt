package dev.nfcstudio.app.core.ndef

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NdefTextCodecTest {

    @Test
    fun `utf-8 round trip with language`() {
        val decoded = NdefTextCodec.decode(NdefTextCodec.encode("Hello", language = "en"))
        assertEquals("Hello", decoded.text)
        assertEquals("en", decoded.language)
        assertEquals(false, decoded.isUtf16)
    }

    @Test
    fun `status byte encodes language length`() {
        val payload = NdefTextCodec.encode("Hi", language = "de")
        assertEquals(2, payload[0].toInt() and 0x3F)
        assertEquals(1 + 2 + 2, payload.size)
    }

    @Test
    fun `utf-16 round trip`() {
        val decoded = NdefTextCodec.decode(NdefTextCodec.encode("Unicode ✓ ümlaut", language = "en", utf16 = true))
        assertEquals("Unicode ✓ ümlaut", decoded.text)
        assertEquals(true, decoded.isUtf16)
    }

    @Test
    fun `utf-16 little endian text with BOM decodes`() {
        val body = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'h'.code.toByte(), 0, 'i'.code.toByte(), 0)
        val payload = byteArrayOf((0x80 or 2).toByte()) + "en".toByteArray() + body
        assertEquals("hi", NdefTextCodec.decode(payload).text)
    }

    @Test
    fun `region subtag language round trip`() {
        assertEquals("pt-BR", NdefTextCodec.decode(NdefTextCodec.encode("olá", language = "pt-BR")).language)
    }

    @Test
    fun `empty payload and oversized language length throw parse exception`() {
        assertThrows(NdefParseException::class.java) { NdefTextCodec.decode(ByteArray(0)) }
        assertThrows(NdefParseException::class.java) { NdefTextCodec.decode(byteArrayOf(10, 1)) }
    }

    @Test
    fun `encode rejects invalid language codes`() {
        listOf("ün", "e n", "", "x").forEach { lang ->
            assertThrows(IllegalArgumentException::class.java) { NdefTextCodec.encode("x", language = lang) }
        }
    }
}
