package com.tapandgo.nfc.hce

/**
 * NFC Forum Type 4 Tag (T4T v2.0) NDEF application, read-only. Pure state
 * machine so every APDU path is unit tested without a phone.
 *
 * Supported: SELECT NDEF application by AID, SELECT CC / NDEF file by ID,
 * READ BINARY. Everything else gets the proper ISO 7816-4 status word; UPDATE
 * BINARY is refused (the emulated tag is read-only). No other AIDs are
 * registered, so payment/transit/access applications are never answered.
 */
class Type4Emulator(ndefMessage: ByteArray) {

    companion object {
        val NDEF_AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        const val CC_FILE_ID = 0xE103
        const val NDEF_FILE_ID = 0xE104
        /** Max NDEF message accepted (NDEF file holds 2-byte NLEN + message). */
        const val MAX_MESSAGE = 0x7FFD
        private const val MLE = 0x00FF
        private const val MLC = 0x00FF

        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)
        val SW_SECURITY = byteArrayOf(0x69, 0x82.toByte())
        val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        val SW_CONDITIONS = byteArrayOf(0x69, 0x85.toByte())
    }

    enum class Selected { NONE, APP, CC, NDEF }

    private val ndefFile: ByteArray
    private val cc: ByteArray
    var selected: Selected = Selected.NONE
        private set

    init {
        require(ndefMessage.isNotEmpty() && ndefMessage.size <= MAX_MESSAGE) { "message size" }
        ndefFile = byteArrayOf((ndefMessage.size ushr 8).toByte(), ndefMessage.size.toByte()) + ndefMessage
        val max = ndefFile.size
        cc = byteArrayOf(
            0x00, 0x0F, // CCLEN
            0x20, // mapping version 2.0
            (MLE ushr 8).toByte(), MLE.toByte(),
            (MLC ushr 8).toByte(), MLC.toByte(),
            0x04, 0x06, // NDEF File Control TLV
            (NDEF_FILE_ID ushr 8).toByte(), NDEF_FILE_ID.toByte(),
            (max ushr 8).toByte(), max.toByte(),
            0x00, // read access: granted
            0xFF.toByte(), // write access: none (read-only)
        )
    }

    /** Field left / reader deselected: the next session starts unselected. */
    fun reset() { selected = Selected.NONE }

    fun process(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_WRONG_LENGTH
        val cla = apdu[0].toInt() and 0xFF
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        if (cla != 0x00) return SW_CLA_NOT_SUPPORTED
        return when (ins) {
            0xA4 -> select(apdu, p1, p2)
            0xB0 -> readBinary(apdu, p1, p2)
            0xD6 -> if (selected == Selected.NONE) SW_CONDITIONS else SW_SECURITY
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(apdu: ByteArray, p1: Int, p2: Int): ByteArray {
        if (apdu.size < 5) return SW_WRONG_LENGTH
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return SW_WRONG_LENGTH
        val data = apdu.copyOfRange(5, 5 + lc)
        return when (p1) {
            0x04 -> if (data.contentEquals(NDEF_AID)) { selected = Selected.APP; SW_OK } else { selected = Selected.NONE; SW_NOT_FOUND }
            0x00 -> {
                if (p2 != 0x0C && p2 != 0x00) return SW_WRONG_P1P2
                if (selected == Selected.NONE || lc != 2) return SW_NOT_FOUND
                when (((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)) {
                    CC_FILE_ID -> { selected = Selected.CC; SW_OK }
                    NDEF_FILE_ID -> { selected = Selected.NDEF; SW_OK }
                    else -> SW_NOT_FOUND
                }
            }
            else -> SW_WRONG_P1P2
        }
    }

    private fun readBinary(apdu: ByteArray, p1: Int, p2: Int): ByteArray {
        val file = when (selected) {
            Selected.CC -> cc
            Selected.NDEF -> ndefFile
            else -> return SW_CONDITIONS
        }
        if (p1 and 0x80 != 0) return SW_WRONG_P1P2 // short EF id addressing not supported
        val offset = (p1 shl 8) or p2
        if (offset > file.size) return SW_WRONG_P1P2
        val le = if (apdu.size >= 5) (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        val end = minOf(file.size, offset + minOf(le, MLE))
        return file.copyOfRange(offset, end) + SW_OK
    }
}
