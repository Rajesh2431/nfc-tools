package dev.nfcstudio.app.data

import dev.nfcstudio.app.core.common.toHex
import dev.nfcstudio.app.core.ndef.NdefMessageCodec
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.toRawRecord
import dev.nfcstudio.app.data.database.ScanEntryEntity
import dev.nfcstudio.app.data.database.TemplateEntity
import dev.nfcstudio.app.data.database.escapeLike
import dev.nfcstudio.app.data.export.ExportCodec
import dev.nfcstudio.app.data.export.ImportResult
import dev.nfcstudio.app.data.export.RecordJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCodecTest {

    private val now = 1_800_000_000_000L
    private val payload = NdefMessageCodec.encode(listOf(RecordSpec.Text("hi").toRawRecord())).toHex()
    private val scan = ScanEntryEntity(
        id = 7, createdAt = now - 1000, summary = "hi", technologies = "NfcA,Ndef", recordCount = 1,
        contentKind = "RECORDS", result = "OK", ndefType = "org.nfcforum.ndef.type2",
        uidHex = "04a1", payloadHex = payload, favourite = true,
    )
    private val template = TemplateEntity(
        id = 3, name = "Wi-Fi poster", description = "", createdAt = now - 5, updatedAt = now - 5,
        recordsJson = RecordJson.encode(listOf(RecordSpec.Uri("https://example.com"), RecordSpec.Text("Hello", "de"))),
    )

    private fun roundTrip(keepPayload: Boolean = true, keepUid: Boolean = true) =
        ExportCodec.decode(ExportCodec.encode(listOf(scan), listOf(template), now), now, keepPayload, keepUid) as ImportResult.Ok

    @Test
    fun `round trip preserves content and assigns new ids`() {
        val b = roundTrip().bundle
        assertEquals(listOf(scan.copy(id = 0)), b.history)
        assertEquals(listOf(template.copy(id = 0)), b.templates)
        assertEquals(0, b.skippedHistory + b.skippedTemplates)
    }

    @Test
    fun `profiles round trip and invalid profiles are skipped`() {
        val good = dev.nfcstudio.app.data.repository.TaskRepository.toEntity(
            dev.nfcstudio.app.domain.task.TaskProfile(
                name = "Door", actions = listOf(dev.nfcstudio.app.domain.task.TaskAction.DisplayText("Welcome")),
                createdAt = now - 10, updatedAt = now - 10,
            ),
        )
        val text = ExportCodec.encode(emptyList(), emptyList(), now, listOf(good))
        val b = (ExportCodec.decode(text, now, true, true) as ImportResult.Ok).bundle
        assertEquals(listOf(good), b.profiles)
        val tampered = text.replace("display_text", "open_url").replace("\"text\": \"Welcome\"", "\"url\": \"javascript:x\"")
        val t = (ExportCodec.decode(tampered, now, true, true) as ImportResult.Ok).bundle
        assertEquals(0, t.profiles.size)
        assertEquals(1, t.skippedProfiles)
    }

    @Test
    fun `privacy settings strip payload and uid on import`() {
        val e = roundTrip(keepPayload = false, keepUid = false).bundle.history.single()
        assertNull(e.payloadHex)
        assertNull(e.uidHex)
    }

    @Test
    fun `rejects non json, wrong format and future schema versions`() {
        assertEquals(ImportResult.NotJson, ExportCodec.decode("{nope", now, true, true))
        assertEquals(ImportResult.WrongFormat, ExportCodec.decode("""{"format":"other","schemaVersion":1}""", now, true, true))
        assertEquals(ImportResult.WrongFormat, ExportCodec.decode("[]", now, true, true))
        assertEquals(
            ImportResult.UnsupportedVersion(2),
            ExportCodec.decode("""{"format":"nfc-studio-export","schemaVersion":2,"exportedAt":1}""", now, true, true),
        )
    }

    @Test
    fun `unknown keys are rejected rather than silently ignored`() {
        val text = """{"format":"nfc-studio-export","schemaVersion":1,"exportedAt":1,"exec":"rm -rf /"}"""
        assertEquals(ImportResult.WrongFormat, ExportCodec.decode(text, now, true, true))
    }

    @Test
    fun `oversized files are rejected before parsing`() {
        assertEquals(ImportResult.TooLarge, ExportCodec.decode(" ".repeat(ExportCodec.MAX_FILE_BYTES + 1), now, true, true))
    }

    @Test
    fun `invalid items are skipped and counted`() {
        val badScan = scan.copy(payloadHex = "zz", createdAt = now - 1)
        val futureScan = scan.copy(createdAt = now + 10L * 24 * 3600 * 1000)
        val badTemplate = template.copy(recordsJson = RecordJson.encode(listOf(RecordSpec.Uri("https://ok.example"))))
        val text = ExportCodec.encode(listOf(scan, badScan, futureScan), listOf(badTemplate), now)
            .replace("\"https://ok.example\"", "\"javascript:alert(1)\"")
        val b = (ExportCodec.decode(text, now, true, true) as ImportResult.Ok).bundle
        assertEquals(1, b.history.size)
        assertEquals(2, b.skippedHistory)
        assertEquals(0, b.templates.size)
        assertEquals(1, b.skippedTemplates)
    }

    @Test
    fun `record json rejects unknown record types and invalid records`() {
        assertNull(RecordJson.decode("""[{"type":"shell","cmd":"x"}]"""))
        assertNull(RecordJson.decode("""[{"type":"text","text":"x","language":"e n"}]"""))
        assertNull(RecordJson.decode("[]"))
        assertEquals(listOf<RecordSpec>(RecordSpec.Text("x", "en")), RecordJson.decode("""[{"type":"text","text":"x"}]"""))
    }

    @Test
    fun `like escaping neutralises wildcards`() {
        assertEquals("100\\%\\_a\\\\b", escapeLike("100%_a\\b"))
        assertTrue(escapeLike("plain") == "plain")
    }
}
