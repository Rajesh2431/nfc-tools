package dev.nfcstudio.app.feature

import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.RecordValidator
import dev.nfcstudio.app.feature.writer.Field
import dev.nfcstudio.app.feature.writer.RecordBuilders
import dev.nfcstudio.app.feature.writer.RecordType
import dev.nfcstudio.app.feature.writer.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecordBuildersTest {
    private val valid = mapOf(
        RecordType.TEXT to mapOf(Field.TEXT to "hi", Field.LANGUAGE to "en"),
        RecordType.URL to mapOf(Field.URL to "https://example.com"),
        RecordType.PHONE to mapOf(Field.PHONE to "+1 555 0100"),
        RecordType.SMS to mapOf(Field.PHONE to "+1555", Field.BODY to "hi"),
        RecordType.EMAIL to mapOf(Field.EMAIL to "a@b.example"),
        RecordType.CONTACT to mapOf(Field.NAME to "Jane"),
        RecordType.LOCATION to mapOf(Field.LATITUDE to "52,52", Field.LONGITUDE to "13.4"),
        RecordType.ADDRESS to mapOf(Field.ADDRESS to "1 Main St"),
        RecordType.WIFI to mapOf(Field.SSID to "Home", Field.WIFI_KEY to "password1"),
        RecordType.BLUETOOTH to mapOf(Field.MAC to "00:11:22:33:44:55"),
        RecordType.APP to mapOf(Field.PACKAGE to "com.example.app"),
        RecordType.MIME to mapOf(Field.MIME_TYPE to "text/plain", Field.PAYLOAD to "x"),
    )

    @Test
    fun `every record type builds a valid record from valid input`() {
        RecordType.entries.forEach { t ->
            val spec = RecordBuilders.build(t, valid.getValue(t))
            assertNotNull(t.name, spec)
            assertNull(t.name, RecordValidator.validate(spec!!))
        }
    }

    @Test
    fun `invalid input yields null rather than a broken record`() {
        assertNull(RecordBuilders.build(RecordType.URL, mapOf(Field.URL to "javascript:alert(1)")))
        assertNull(RecordBuilders.build(RecordType.LOCATION, mapOf(Field.LATITUDE to "north", Field.LONGITUDE to "1")))
        assertNull(RecordBuilders.build(RecordType.WIFI, mapOf(Field.SSID to "Home", Field.WIFI_KEY to "short")))
        assertNull(RecordBuilders.build(RecordType.TEXT, mapOf(Field.TEXT to "x", Field.LANGUAGE to "e n")))
        assertNull(RecordBuilders.build(RecordType.MIME, mapOf(Field.MIME_TYPE to "no-slash", Field.PAYLOAD to "x")))
    }

    @Test
    fun `open wifi ignores a typed key`() {
        val spec = RecordBuilders.build(RecordType.WIFI, mapOf(Field.SSID to "Cafe", Field.WIFI_KEY to "ignored"), WifiSecurity.OPEN)
        assertEquals(true, spec is RecordSpec.MimeBinary)
    }
}
