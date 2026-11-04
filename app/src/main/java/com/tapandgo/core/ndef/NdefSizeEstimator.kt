package com.tapandgo.core.ndef

/**
 * Encoded-size preview before writing (spec 4.3). [messageSize] uses the same
 * encoder that produces the bytes written to the tag, so the estimate is exact
 * for the NDEF message itself (the tag's TLV wrapper is not included; the
 * platform's Ndef.getMaxSize() already accounts for it).
 */
object NdefSizeEstimator {

    /** Bytes a record with the given field lengths occupies inside a message. */
    fun recordSize(typeLength: Int, payloadLength: Int, idLength: Int = 0): Int {
        val lengthField = if (payloadLength <= 255) 1 else 4
        val idField = if (idLength > 0) 1 + idLength else 0
        return 1 /* header */ + 1 /* type length */ + lengthField + idField + typeLength + payloadLength
    }

    /** Exact encoded size of the message built from [specs]; 0 when empty. */
    fun messageSize(specs: List<RecordSpec>): Int =
        if (specs.isEmpty()) 0 else NdefMessageCodec.encode(specs.map { it.toRawRecord() }).size
}
