package com.tapandgo.core.nfc

import com.tapandgo.core.common.hexToBytesOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipParsingTest {
    private fun v(hex: String) = ChipParsing.parseNxpVersion(hex.hexToBytesOrNull()!!)

    @Test
    fun `identifies ntag21x from get_version`() {
        assertEquals("NTAG213", v("0004040201000F03")!!.name)
        val n215 = v("0004040201001103")!!
        assertEquals("NTAG215", n215.name)
        assertEquals(504, n215.userBytes)
        assertEquals(135, n215.unitCount)
        assertEquals(0x83, n215.ntag!!.cfg0)
        assertEquals(0xE3, v("0004040201001303")!!.ntag!!.cfg0)
        assertNull(v("0004040201000B03")!!.ntag) // NTAG210: identified, no password support
    }

    @Test
    fun `identifies ultralight ev1 and rejects garbage`() {
        assertEquals("MIFARE Ultralight EV1 (MF0UL11)", v("0004030101000B03")!!.name)
        assertNull(ChipParsing.parseNxpVersion(byteArrayOf(0x00)))
        assertNull(v("0005040201000F03")) // not NXP
    }

    @Test
    fun `parses iso15693 system info`() {
        // flags 00, info 0F (dsfid, afi, mem, ic), uid(8), dsfid, afi, blocks-1=0x1B (28), size-1=03 (4), ic ref
        val r = "000F" + "0102030405060704E0" .take(16) + "00" + "00" + "1B03" + "01"
        val uid = "E004010203040506".hexToBytesOrNull()!!.reversedArray() // LSB first: [6] = 0x04 NXP
        val chip = ChipParsing.parseNfcVSystemInfo(r.hexToBytesOrNull()!!, uid)!!
        assertEquals(28, chip.unitCount)
        assertEquals(4, chip.unitSize)
        assertEquals(112, chip.totalBytes)
        assertEquals("NXP", chip.vendor)
        assertNull(ChipParsing.parseNfcVSystemInfo(byteArrayOf(0x01, 0x0F), null)) // error flag
    }

    @Test
    fun `type 2 nak detection`() {
        assertTrue(ChipParsing.isType2Nak(byteArrayOf(0x00)))
        assertTrue(ChipParsing.isType2Nak(byteArrayOf(0x04)))
        assertFalse(ChipParsing.isType2Nak(byteArrayOf(0x0A)))
        assertFalse(ChipParsing.isType2Nak(ByteArray(16)))
    }

    @Test
    fun `password derivation is stable and writes auth0 last`() {
        val (pwd, pack) = ChipParsing.derivePassword("secret")
        assertEquals(4, pwd.size); assertEquals(2, pack.size)
        // SHA-256("secret") = 2bb80d53 7b1d ...
        assertArrayEquals("2bb80d53".hexToBytesOrNull(), pwd)
        assertArrayEquals("7b1d".hexToBytesOrNull(), pack)
        val layout = v("0004040201001103")!!.ntag!!
        val cfg0 = "040000FF".hexToBytesOrNull()!!
        val cfg1 = "00050000".hexToBytesOrNull()!!
        val writes = ChipParsing.passwordWrites(layout, pwd, pack, cfg0, cfg1, protectReads = true)
        assertEquals(listOf(0x85, 0x86, 0x84, 0x83), writes.map { it.first })
        assertEquals(0x80, writes[2].second[0].toInt() and 0xFF) // PROT set
        assertEquals(4, writes[3].second[3].toInt()) // AUTH0 = first user page, last
        assertFalse(ChipParsing.isProtected(layout, cfg0))
        assertTrue(ChipParsing.isProtected(layout, writes[3].second))
        val removal = ChipParsing.removalWrites(layout, writes[3].second, writes[2].second)
        assertEquals(0x83, removal.first().first) // AUTH0 disabled first
        assertEquals(0xFF, removal.first().second[3].toInt() and 0xFF)
    }
}
