package com.tapandgo.core.apdu

import com.tapandgo.core.apdu.CommandApdu.Parse
import com.tapandgo.core.apdu.CommandApdu.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApduTest {
    private fun parse(hex: String, max: Int = 261, ext: Boolean = false) = CommandApdu.parse(hex, max, ext)
    private fun ok(hex: String, ext: Boolean = false) = (parse(hex, 65_544, ext) as Parse.Ok).apdu

    @Test
    fun `accepts all four short cases`() {
        ok("00A40400") // case 1
        ok("00B0000010") // case 2
        ok("00A4040007D2760000850101") // case 3
        ok("00 A4 04 00 07 D2 76 00 00 85 01 01 00") // case 4, spaces
    }

    @Test
    fun `rejects malformed commands`() {
        assertEquals(Parse.Invalid(Problem.NOT_HEX), parse("00A4G400"))
        assertEquals(Parse.Invalid(Problem.NOT_HEX), parse("00A404"+"0"))
        assertEquals(Parse.Invalid(Problem.TOO_SHORT), parse("00A404"))
        assertEquals(Parse.Invalid(Problem.BAD_LENGTH), parse("00A4040005D276"))
        assertEquals(Parse.Invalid(Problem.INVALID_CLA), parse("FFCA000000"))
        assertEquals(Parse.Invalid(Problem.TOO_LONG), parse("00D60000FF" + "00".repeat(255) + "00", max = 32))
    }

    @Test
    fun `extended length requires platform support`() {
        val ext = "00B00000" + "000100"
        assertEquals(Parse.Invalid(Problem.EXTENDED_UNSUPPORTED), parse(ext))
        ok(ext, ext = true)
        ok("00D60000" + "000003" + "AABBCC", ext = true)
        assertTrue(CommandApdu.parse("00D60000" + "000004" + "AABBCC", 65_544, true) is Parse.Invalid)
    }

    @Test
    fun `state changing and proprietary commands are classified`() {
        assertFalse(ApduSafety.isStateChanging(ok("00A4040007D2760000850101")))
        assertFalse(ApduSafety.isStateChanging(ok("00B0000010")))
        assertTrue(ApduSafety.isStateChanging(ok("00D6000001AA"))) // UPDATE BINARY
        assertTrue(ApduSafety.isStateChanging(ok("0020008008" + "31323334FFFFFFFF"))) // VERIFY
        assertTrue(ApduSafety.isStateChanging(ok("00E40000"))) // DELETE FILE
        assertTrue(ApduSafety.isStateChanging(ok("90FC000000"))) // DESFire FORMAT (proprietary CLA)
    }

    @Test
    fun `response parsing and status words`() {
        val r = ResponseApdu.parse(byteArrayOf(0x01, 0x02, 0x90.toByte(), 0x00))!!
        assertEquals(0x9000, r.sw)
        assertEquals(2, r.data.size)
        assertNull(ResponseApdu.parse(byteArrayOf(0x90.toByte())))
        assertEquals("Success", StatusWords.describe(0x90, 0x00))
        assertEquals("Verification failed; 2 tries left", StatusWords.describe(0x63, 0xC2))
        assertEquals("File or application not found", StatusWords.describe(0x6A, 0x82))
        assertEquals("Wrong Le; exact length is 16", StatusWords.describe(0x6C, 0x10))
    }
}
