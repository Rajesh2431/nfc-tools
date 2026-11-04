package com.tapandgo.core.ndef

import com.tapandgo.core.common.hexToBytesOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RichRecordsTest {

    private fun decode(spec: RecordSpec) = NdefParser.decode(spec.toRawRecord())
    private fun detail(r: DecodedRecord, l: Detail.Label) = r.details.first { it.label == l }.value

    @Test
    fun `uri builders encode safely`() {
        assertEquals(RecordSpec.Uri("tel:+4930123"), RichRecords.phone("+49 30 123"))
        assertEquals(RecordSpec.Uri("sms:+123?body=Hi%20there%26more"), RichRecords.sms("+1 23", "Hi there&more"))
        assertEquals(RecordSpec.Uri("mailto:a@b.example?subject=Hello%20you"), RichRecords.email("a@b.example", "Hello you", ""))
        assertEquals(RecordSpec.Uri("geo:52.520000,13.405000"), RichRecords.location(52.52, 13.405))
        assertEquals(RecordSpec.Uri("geo:0,0?q=1%20Main%20St"), RichRecords.address("1 Main St"))
        assertNull(RichRecords.phone("call me"))
        assertNull(RichRecords.location(91.0, 0.0))
        // Every built URI passes the app's own policy.
        listOf(RichRecords.phone("123"), RichRecords.sms("123", "x"), RichRecords.email("a@b.example", "s", "b"), RichRecords.address("x"))
            .forEach { assertNull(RecordValidator.validate(it!!)) }
    }

    @Test
    fun `vcard round trip with escaping`() {
        val spec = RichRecords.contact(RichRecords.Contact("Doe, Jane; Dr", phone = "+1 555", email = "j@d.example", organization = "ACME", address = "1 Main St"))!!
        val r = decode(spec)
        assertEquals(RecordKind.CONTACT, r.kind)
        assertEquals("Doe, Jane; Dr", detail(r, Detail.Label.NAME))
        assertEquals("+1 555", detail(r, Detail.Label.PHONE))
        assertEquals("1 Main St", detail(r, Detail.Label.ADDRESS))
        assertNull(RichRecords.contact(RichRecords.Contact("")))
        assertNull(RichRecords.contact(RichRecords.Contact("x", url = "javascript:alert(1)")))
    }

    @Test
    fun `vcard decoder unfolds lines and rejects non vcards`() {
        val folded = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Very Long\r\n  Name\r\nEND:VCARD\r\n".toByteArray()
        assertEquals("Very Long Name", RichRecords.decodeContact(folded).single().value)
        assertThrows(NdefParseException::class.java) { RichRecords.decodeContact("hello".toByteArray()) }
    }

    @Test
    fun `wifi credential round trip and masking`() {
        val spec = RichRecords.wifi(RichRecords.Wifi("Home Net", RichRecords.WifiAuth.WPA2_PERSONAL, "correct horse"))!!
        val r = decode(spec)
        assertEquals(RecordKind.WIFI, r.kind)
        assertEquals("Home Net", detail(r, Detail.Label.SSID))
        assertEquals("WPA2_PERSONAL", detail(r, Detail.Label.AUTHENTICATION))
        val key = r.details.first { it.label == Detail.Label.NETWORK_KEY }
        assertEquals("correct horse", key.value)
        assertTrue(key.secret)
        // Summaries never show the secret.
        assertEquals("Home Net", r.summarize())
    }

    @Test
    fun `wifi wire format starts with version then credential`() {
        val spec = RichRecords.wifi(RichRecords.Wifi("A", RichRecords.WifiAuth.OPEN, ""))!! as RecordSpec.MimeBinary
        val b = spec.payloadHex.hexToBytesOrNull()!!
        assertEquals("104a000110100e", spec.payloadHex.substring(0, 14))
        assertEquals(RichRecords.WIFI_MIME, spec.mimeType)
        assertTrue(b.size > 20)
    }

    @Test
    fun `wifi validation`() {
        assertNull(RichRecords.wifi(RichRecords.Wifi("", RichRecords.WifiAuth.OPEN, "")))
        assertNull(RichRecords.wifi(RichRecords.Wifi("x".repeat(33), RichRecords.WifiAuth.OPEN, "")))
        assertNull(RichRecords.wifi(RichRecords.Wifi("n", RichRecords.WifiAuth.WPA2_PERSONAL, "short")))
        assertThrows(NdefParseException::class.java) { RichRecords.decodeWifi(byteArrayOf(0x10, 0x0E, 0x00, 0x10, 1)) }
    }

    @Test
    fun `bluetooth oob round trip`() {
        val spec = RichRecords.bluetooth("00:11:22:AA:BB:CC", "Speaker")!!
        val raw = (spec as RecordSpec.MimeBinary).payloadHex
        assertEquals("1100" + "ccbbaa221100", raw.substring(0, 16)) // length 17 LE + reversed address
        val r = decode(spec)
        assertEquals(RecordKind.BLUETOOTH, r.kind)
        assertEquals("00:11:22:AA:BB:CC", detail(r, Detail.Label.MAC))
        assertEquals("Speaker", detail(r, Detail.Label.DEVICE_NAME))
        assertNull(RichRecords.bluetooth("not-a-mac", ""))
        assertThrows(NdefParseException::class.java) { RichRecords.decodeBluetooth(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `app launch and task tag records`() {
        val app = decode(RichRecords.appLaunch("com.example.app")!!)
        assertEquals(RecordKind.APP, app.kind)
        assertEquals("com.example.app", detail(app, Detail.Label.PACKAGE))
        assertNull(RichRecords.appLaunch("not a package"))
        val token = "123e4567-e89b-12d3-a456-426614174000"
        val task = decode(RichRecords.taskTag(token)!!)
        assertEquals(RecordKind.TASK, task.kind)
        assertEquals(token, detail(task, Detail.Label.TOKEN))
        assertNull(RichRecords.taskTag("../../etc"))
        // Malformed task payload is shown as plain external type, never trusted.
        val forged = NdefParser.decode(NdefParser.RawRecord(4, RichRecords.TASK_TYPE.toByteArray(), "rm -rf /".toByteArray()))
        assertEquals(RecordKind.EXTERNAL, forged.kind)
    }

    @Test
    fun `binary records survive template json`() {
        val specs = listOf(
            RichRecords.wifi(RichRecords.Wifi("N", RichRecords.WifiAuth.WPA2_PERSONAL, "password1"))!!,
            RichRecords.appLaunch("com.example.app")!!,
        )
        assertEquals(specs, com.tapandgo.data.export.RecordJson.decode(com.tapandgo.data.export.RecordJson.encode(specs)))
    }
}
