package dev.nfcstudio.app.core.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HexAndRedactTest {

    @Test
    fun `hex round trip and grouping`() {
        val bytes = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        assertEquals("007f80ff", bytes.toHex())
        assertArrayEquals(bytes, "00 7F:80ff".hexToBytesOrNull())
        assertEquals("00 7F 80 FF", bytes.toHex().groupHexPairs())
    }

    @Test
    fun `invalid hex is rejected`() {
        assertNull("abc".hexToBytesOrNull())
        assertNull("zz".hexToBytesOrNull())
    }

    @Test
    fun `platform messages lose identifiers`() {
        val redacted = Redact.platformMessage("Permission Denial: Tag ( ID: 04 A2 3B 1C ) is out of date; uid 04a23b1c5d6e")!!
        assertFalse(redacted.contains("04a23b1c5d6e"))
        assertFalse(redacted.contains("04 A2"))
    }

    @Test
    fun `text redaction keeps only length`() {
        assertEquals("<redacted 5 chars>", Redact.text("hello"))
        assertEquals("IllegalStateException", Redact.throwable(IllegalStateException("secret")))
    }
}
