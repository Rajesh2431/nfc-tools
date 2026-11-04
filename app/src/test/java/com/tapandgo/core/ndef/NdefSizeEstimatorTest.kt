package com.tapandgo.core.ndef

import org.junit.Assert.assertEquals
import org.junit.Test

class NdefSizeEstimatorTest {

    @Test
    fun `short record header is four bytes for a one byte type`() {
        // header(1) + typeLen(1) + payloadLen(1) + type(1)
        assertEquals(4 + 10, NdefSizeEstimator.recordSize(typeLength = 1, payloadLength = 10))
    }

    @Test
    fun `long form uses four byte payload length`() {
        assertEquals(7 + 300, NdefSizeEstimator.recordSize(typeLength = 1, payloadLength = 300))
    }

    @Test
    fun `id field adds length byte and id`() {
        assertEquals(4 + 1 + 3 + 10, NdefSizeEstimator.recordSize(typeLength = 1, payloadLength = 10, idLength = 3))
    }

    @Test
    fun `text record estimate matches recordSize formula`() {
        val payloadSize = NdefTextCodec.encode("hello world", "en").size
        assertEquals(
            NdefSizeEstimator.recordSize(1, payloadSize),
            NdefSizeEstimator.messageSize(listOf(RecordSpec.Text("hello world", "en"))),
        )
    }

    @Test
    fun `utf-16 text switches to long form above 255 bytes`() {
        val spec = RecordSpec.Text(text = "ä".repeat(300), encodeAsUtf16 = true)
        val payload = 1 + 2 + 600
        assertEquals(7 + payload, NdefSizeEstimator.messageSize(listOf(spec)))
    }
}
