package dev.nfcstudio.app.core.ndef

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordValidatorTest {

    @Test
    fun `valid records pass`() {
        assertNull(RecordValidator.validate(RecordSpec.Text("hi", "en")))
        assertNull(RecordValidator.validate(RecordSpec.Uri("https://example.com")))
        assertNull(RecordValidator.validate(RecordSpec.Mime("application/vnd.example+json", "{}")))
    }

    @Test
    fun `text problems are reported, never thrown`() {
        assertEquals(RecordProblem.TEXT_EMPTY, RecordValidator.validate(RecordSpec.Text("  ", "en")))
        assertEquals(RecordProblem.LANGUAGE_INVALID, RecordValidator.validate(RecordSpec.Text("x", "e n")))
        assertEquals(RecordProblem.LANGUAGE_INVALID, RecordValidator.validate(RecordSpec.Text("x", "ü")))
        assertEquals(
            RecordProblem.TEXT_TOO_LONG,
            RecordValidator.validate(RecordSpec.Text("a".repeat(RecordValidator.MAX_PAYLOAD_BYTES + 1), "en")),
        )
    }

    @Test
    fun `uri and mime problems`() {
        assertEquals(RecordProblem.URI_INVALID, RecordValidator.validate(RecordSpec.Uri("javascript:x")))
        assertEquals(RecordProblem.MIME_INVALID, RecordValidator.validate(RecordSpec.Mime("Text/Plain", "x")))
        assertEquals(RecordProblem.MIME_INVALID, RecordValidator.validate(RecordSpec.Mime("noslash", "x")))
    }
}
