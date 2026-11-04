package dev.nfcstudio.app.core.ndef

import dev.nfcstudio.app.core.common.hexToBytesOrNull
import dev.nfcstudio.app.core.ndef.NdefParser.RawRecord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NdefMessageCodecTest {

    @Test
    fun `encodes the canonical short uri record`() {
        // D1 01 0D 55 04 "example.com/" — MB|ME|SR, TNF=1, type "U", prefix https://
        val bytes = NdefMessageCodec.encode(listOf(RecordSpec.Uri("https://example.com/").toRawRecord()))
        assertArrayEquals("d1010d55046578616d706c652e636f6d2f".hexToBytesOrNull(), bytes)
    }

    @Test
    fun `round trips multiple records with flags`() {
        val records = listOf(
            RecordSpec.Text("hi").toRawRecord(),
            RecordSpec.Uri("tel:+1").toRawRecord(),
            RecordSpec.Mime("text/plain", "x".repeat(300)).toRawRecord(), // long-form record
        )
        val bytes = NdefMessageCodec.encode(records)
        assertEquals(0x91, bytes[0].toInt() and 0xFF) // MB|SR|TNF1
        assertEquals(records, NdefMessageCodec.parse(bytes))
    }

    @Test
    fun `round trips record ids`() {
        val r = RawRecord(tnf = 2, type = "a/b".toByteArray(), payload = byteArrayOf(1), id = byteArrayOf(9, 9))
        assertEquals(listOf(r), NdefMessageCodec.parse(NdefMessageCodec.encode(listOf(r))))
    }

    @Test
    fun `rejects truncated input`() {
        val bytes = NdefMessageCodec.encode(listOf(RecordSpec.Text("hello").toRawRecord()))
        for (n in 1 until bytes.size) {
            assertThrows(NdefParseException::class.java) { NdefMessageCodec.parse(bytes.copyOf(n)) }
        }
    }

    @Test
    fun `huge declared payload length does not allocate`() {
        // long-form record claiming a 2 GiB payload
        val crafted = byteArrayOf(0xC1.toByte(), 0x01, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x54)
        assertThrows(NdefParseException::class.java) { NdefMessageCodec.parse(crafted) }
    }

    @Test
    fun `rejects missing MB, chunked records, trailing bytes and missing ME`() {
        assertThrows(NdefParseException::class.java) { NdefMessageCodec.parse(byteArrayOf(0x51, 0, 0)) } // no MB
        assertThrows(NdefParseException::class.java) { NdefMessageCodec.parse(byteArrayOf(0xB1.toByte(), 1, 0, 0x54)) } // CF
        assertThrows(NdefParseException::class.java) { NdefMessageCodec.parse(byteArrayOf(0xD0.toByte(), 0, 0, 0)) } // trailing
        assertThrows(NdefParseException::class.java) { NdefMessageCodec.parse(byteArrayOf(0x90.toByte(), 0, 0)) } // no ME
        assertThrows(NdefParseException::class.java) { NdefMessageCodec.parse(ByteArray(0)) }
    }

    @Test
    fun `size estimator equals encoded length`() {
        val specs = listOf(RecordSpec.Text("hello", "en"), RecordSpec.Uri("https://www.example.com"))
        assertEquals(
            NdefMessageCodec.encode(specs.map { it.toRawRecord() }).size,
            NdefSizeEstimator.messageSize(specs),
        )
        assertEquals(0, NdefSizeEstimator.messageSize(emptyList()))
    }
}
