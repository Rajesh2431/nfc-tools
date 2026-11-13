package com.tapandgo.core

import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.common.toHex
import com.tapandgo.nfc.hce.Type4Emulator
import org.junit.Assert.assertEquals
import org.junit.Test

class Type4EmulatorTest {
    private val message = "d101055402656e6869".hexToBytesOrNull()!!
    private fun Type4Emulator.send(hex: String) = process(hex.hexToBytesOrNull()!!).toHex().lowercase()

    @Test
    fun `full reader sequence returns the NDEF message`() {
        val e = Type4Emulator(message)
        assertEquals("9000", e.send("00A4040007D276000085010100"))
        assertEquals("9000", e.send("00A4000C02E103"))
        val cc = e.send("00B000000F")
        assertEquals("000f20", cc.take(6))
        assertEquals("0406e104", cc.substring(14, 22))
        assertEquals("000b", cc.substring(22, 26)) // file size = 2 + 9
        assertEquals("00ff9000", cc.takeLast(8)) // read granted, write denied
        assertEquals("9000", e.send("00A4000C02E104"))
        assertEquals("00099000", e.send("00B0000002"))
        assertEquals("d101055402656e68699000", e.send("00B0000209"))
    }

    @Test
    fun `refuses writes, unknown AIDs, files and instructions`() {
        val e = Type4Emulator(message)
        assertEquals("6985", e.send("00B0000002")) // nothing selected
        assertEquals("6a82", e.send("00A4040007A0000000041010")) // payment AID: never ours
        assertEquals("6a82", e.send("00A4000C02E104")) // file before app
        e.send("00A4040007D2760000850101")
        assertEquals("6a82", e.send("00A4000C02E105"))
        e.send("00A4000C02E104")
        assertEquals("6982", e.send("00D60000020000"))
        assertEquals("6d00", e.send("00CA000000"))
        assertEquals("6e00", e.send("80A4040000"))
        assertEquals("6700", e.send("00A4"))
        assertEquals("6b00", e.send("00B0FF0001"))
    }

    @Test
    fun `reset forgets selection`() {
        val e = Type4Emulator(message)
        e.send("00A4040007D2760000850101")
        e.reset()
        assertEquals(Type4Emulator.Selected.NONE, e.selected)
    }

    @Test
    fun `entitlement resolution never unlocks release builds`() {
        val r = com.tapandgo.billing.EntitlementResolver
        assertEquals(false, r.resolve(purchased = false, devAvailable = false, devEnabled = true).premium)
        assertEquals(com.tapandgo.billing.EntitlementSource.DEVELOPMENT, r.resolve(false, devAvailable = true, devEnabled = true).source)
        assertEquals(com.tapandgo.billing.EntitlementSource.PLAY_BILLING, r.resolve(true, devAvailable = true, devEnabled = true).source)
        assertEquals(false, r.resolve(false, devAvailable = true, devEnabled = false).premium)
    }
}
