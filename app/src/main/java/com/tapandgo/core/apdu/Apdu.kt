package com.tapandgo.core.apdu

import com.tapandgo.core.common.hexToBytesOrNull

/** ISO/IEC 7816-4 command APDU, structurally validated. */
class CommandApdu private constructor(val bytes: ByteArray) {
    val cla: Int get() = bytes[0].toInt() and 0xFF
    val ins: Int get() = bytes[1].toInt() and 0xFF

    /** Interindustry class (b8 = 0) — only these have standardised meaning. */
    val isInterindustry: Boolean get() = cla and 0x80 == 0

    enum class Problem { NOT_HEX, TOO_SHORT, TOO_LONG, BAD_LENGTH, EXTENDED_UNSUPPORTED, INVALID_CLA }

    sealed interface Parse {
        data class Ok(val apdu: CommandApdu) : Parse
        data class Invalid(val problem: Problem) : Parse
    }

    companion object {
        /**
         * Validates hex, size against [maxLength] (the platform's max transceive
         * length) and the case 1–4 short/extended length structure.
         */
        fun parse(hex: String, maxLength: Int, extendedSupported: Boolean): Parse {
            val b = hex.hexToBytesOrNull() ?: return Parse.Invalid(Problem.NOT_HEX)
            if (b.size < 4) return Parse.Invalid(Problem.TOO_SHORT)
            if (b.size > maxLength.coerceAtLeast(4)) return Parse.Invalid(Problem.TOO_LONG)
            if (b[0] == 0xFF.toByte()) return Parse.Invalid(Problem.INVALID_CLA)
            val body = b.size - 4
            val p5 = if (body > 0) b[4].toInt() and 0xFF else -1
            val ok = when {
                body == 0 -> true // case 1
                body == 1 -> true // case 2 short (Le)
                p5 != 0 -> body == 1 + p5 || body == 2 + p5 // case 3/4 short
                else -> { // extended: 00 + 2-byte length
                    if (!extendedSupported) return Parse.Invalid(Problem.EXTENDED_UNSUPPORTED)
                    when {
                        body == 3 -> true // case 2 extended
                        body > 3 -> {
                            val lc = ((b[5].toInt() and 0xFF) shl 8) or (b[6].toInt() and 0xFF)
                            lc > 0 && (body == 3 + lc || body == 5 + lc) // case 3/4 extended
                        }

                        else -> false
                    }
                }
            }
            return if (ok) Parse.Ok(CommandApdu(b)) else Parse.Invalid(Problem.BAD_LENGTH)
        }
    }
}

/**
 * Classifies commands that can change card state. Blocked by default
 * (spec 4.6: "no destructive commands by default"). Proprietary-class
 * commands cannot be classified, so they are treated as state-changing.
 */
object ApduSafety {
    /** Interindustry INS codes that write, erase, create/delete, block or change PIN state. */
    val STATE_CHANGING_INS: Set<Int> = setOf(
        0x04, // DEACTIVATE FILE
        0x0E, 0x0F, // ERASE BINARY
        0x20, 0x21, // VERIFY (wrong PINs consume retry counters)
        0x24, // CHANGE REFERENCE DATA
        0x26, 0x28, // DISABLE / ENABLE VERIFICATION REQUIREMENT
        0x2C, // RESET RETRY COUNTER
        0x44, // ACTIVATE FILE
        0x46, 0x47, // GENERATE ASYMMETRIC KEY PAIR
        0xD0, 0xD1, // WRITE BINARY
        0xD2, // WRITE RECORD
        0xD6, 0xD7, // UPDATE BINARY
        0xDA, 0xDB, // PUT DATA
        0xDC, 0xDD, // UPDATE RECORD
        0xE0, // CREATE FILE
        0xE2, // APPEND RECORD
        0xE4, // DELETE FILE
        0xE6, 0xE8, // TERMINATE DF / EF
        0xFE, // TERMINATE CARD USAGE
    )

    fun isStateChanging(apdu: CommandApdu): Boolean = !apdu.isInterindustry || apdu.ins in STATE_CHANGING_INS
}

/** Response APDU: data + status word. */
data class ResponseApdu(val data: ByteArray, val sw1: Int, val sw2: Int) {
    val sw: Int get() = (sw1 shl 8) or sw2

    override fun equals(other: Any?) = other is ResponseApdu && data.contentEquals(other.data) && sw == other.sw
    override fun hashCode() = data.contentHashCode() * 31 + sw

    companion object {
        /** @return null when the card returned fewer than 2 bytes (no status word). */
        fun parse(raw: ByteArray): ResponseApdu? {
            if (raw.size < 2) return null
            return ResponseApdu(raw.copyOf(raw.size - 2), raw[raw.size - 2].toInt() and 0xFF, raw[raw.size - 1].toInt() and 0xFF)
        }
    }
}

/** Plain-English meaning of common ISO 7816-4 status words. */
object StatusWords {
    fun describe(sw1: Int, sw2: Int): String {
        val sw = (sw1 shl 8) or sw2
        return when {
            sw == 0x9000 -> "Success"
            sw1 == 0x61 -> "Success; $sw2 more bytes available (GET RESPONSE)"
            sw == 0x6281 -> "Warning: returned data may be corrupted"
            sw == 0x6282 -> "Warning: end of file reached before Le bytes"
            sw == 0x6283 -> "Warning: selected file deactivated"
            sw1 == 0x63 && sw2 and 0xF0 == 0xC0 -> "Verification failed; ${sw2 and 0x0F} tries left"
            sw1 == 0x63 -> "Warning: non-volatile memory changed"
            sw1 == 0x64 -> "Error: memory unchanged"
            sw1 == 0x65 -> "Error: memory changed"
            sw == 0x6700 -> "Wrong length"
            sw1 == 0x68 -> "Function in CLA not supported"
            sw == 0x6982 -> "Security status not satisfied"
            sw == 0x6983 -> "Authentication method blocked"
            sw == 0x6984 -> "Referenced data invalidated"
            sw == 0x6985 -> "Conditions of use not satisfied"
            sw == 0x6986 -> "Command not allowed (no current EF)"
            sw1 == 0x69 -> "Command not allowed"
            sw == 0x6A80 -> "Incorrect data field"
            sw == 0x6A81 -> "Function not supported"
            sw == 0x6A82 -> "File or application not found"
            sw == 0x6A83 -> "Record not found"
            sw == 0x6A84 -> "Not enough memory"
            sw == 0x6A86 -> "Incorrect P1/P2"
            sw == 0x6A88 -> "Referenced data not found"
            sw1 == 0x6A -> "Wrong parameters P1/P2"
            sw == 0x6B00 -> "Wrong parameters P1/P2"
            sw1 == 0x6C -> "Wrong Le; exact length is $sw2"
            sw == 0x6D00 -> "Instruction not supported"
            sw == 0x6E00 -> "Class not supported"
            sw == 0x6F00 -> "No precise diagnosis"
            sw1 == 0x91 -> "Proprietary status (e.g. DESFire): %02X".format(sw2)
            else -> "Unknown status"
        }
    }
}
