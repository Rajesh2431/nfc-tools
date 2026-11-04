package dev.nfcstudio.app.feature

import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.nfc.NfcState
import dev.nfcstudio.app.feature.advanced.DiagnosticsBuilder
import dev.nfcstudio.app.testing.ndefTag
import dev.nfcstudio.app.testing.recordsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun `diagnostics never contain payload or uid`() {
        val content = recordsContent(RecordSpec.Text("secret-door-code")).copy(rawMessageHex = "d1010f54")
        val snap = ndefTag(content = content)
        val d = DiagnosticsBuilder.build(snap, NfcState.READY, "1.0.0", "Acme", "Phone", 37, listOf("OK", "TAG_LOST", "TAG_LOST", "IO"))
        val text = DiagnosticsBuilder.encode(d)
        assertFalse(text.contains("secret-door-code"))
        assertFalse(text.contains("d1010f54"))
        assertFalse(text.contains(snap.uidHex!!))
        assertEquals(mapOf("TAG_LOST" to 2, "IO" to 1), d.recentErrorCodes)
        assertEquals(1, d.ndefRecordCount)
    }
}
