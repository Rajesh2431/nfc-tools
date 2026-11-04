package dev.nfcstudio.app.data

import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.data.database.ContentKind
import dev.nfcstudio.app.data.repository.ScanEntryFactory
import dev.nfcstudio.app.data.settings.AppSettings
import dev.nfcstudio.app.testing.ndefTag
import dev.nfcstudio.app.testing.plainTag
import dev.nfcstudio.app.testing.recordsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanEntryFactoryTest {

    private val content = recordsContent(RecordSpec.Text("hello"), RecordSpec.Uri("https://example.com")).copy(rawMessageHex = "d10102")

    @Test
    fun `defaults store summary but never payload or uid`() {
        val e = ScanEntryFactory.create(NfcResult.Ok(ndefTag(content = content)), AppSettings(), now = 42)!!
        assertEquals("hello · https://example.com", e.summary)
        assertEquals(2, e.recordCount)
        assertEquals(ContentKind.RECORDS.name, e.contentKind)
        assertEquals("NfcA,MifareUltralight,Ndef", e.technologies)
        assertNull(e.payloadHex)
        assertNull(e.uidHex)
        assertEquals(42, e.createdAt)
    }

    @Test
    fun `payload and uid are stored only when enabled`() {
        val s = AppSettings(savePayload = true, storeUid = true)
        val e = ScanEntryFactory.create(NfcResult.Ok(ndefTag(content = content)), s, 1)!!
        assertEquals("d10102", e.payloadHex)
        assertEquals("04a1b2c3", e.uidHex)
    }

    @Test
    fun `history disabled stores nothing`() {
        assertNull(ScanEntryFactory.create(NfcResult.Ok(ndefTag()), AppSettings(historyEnabled = false), 1))
    }

    @Test
    fun `errors and non ndef tags are classified`() {
        val err = ScanEntryFactory.create(NfcResult.Err(NfcError.TagLost), AppSettings(), 1)!!
        assertEquals("TAG_LOST", err.result)
        assertEquals(ContentKind.ERROR.name, err.contentKind)
        assertEquals(ContentKind.NO_NDEF.name, ScanEntryFactory.create(NfcResult.Ok(plainTag()), AppSettings(), 1)!!.contentKind)
        assertEquals(ContentKind.MALFORMED.name, ScanEntryFactory.create(NfcResult.Ok(ndefTag(content = NdefContent.Malformed)), AppSettings(), 1)!!.contentKind)
    }
}
