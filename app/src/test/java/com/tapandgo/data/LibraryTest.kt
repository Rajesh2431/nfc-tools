package com.tapandgo.data

import com.tapandgo.core.common.toHex
import com.tapandgo.core.ndef.NdefMessageCodec
import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.ndef.toRawRecord
import com.tapandgo.data.export.ExportCodec
import com.tapandgo.data.export.ImportResult
import com.tapandgo.data.library.Difference
import com.tapandgo.data.library.Library
import com.tapandgo.data.library.LibraryIcon
import com.tapandgo.data.library.LibraryMeta
import com.tapandgo.testing.ndefTag
import com.tapandgo.testing.recordsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val msg = NdefMessageCodec.encode(listOf(RecordSpec.Uri("https://example.com").toRawRecord()))
    private val snap = ndefTag(content = recordsContent(RecordSpec.Uri("https://example.com")).copy(rawMessageHex = msg.toHex(), sizeBytes = msg.size))
    private val meta = LibraryMeta("Front door", folder = "Home", labels = listOf("door", "smart"), icon = LibraryIcon.HOME)

    @Test
    fun `entity keeps payload and metadata`() {
        val e = Library.entityFrom(snap, meta, now = 5)
        assertEquals(msg.toHex(), e.payloadHex)
        assertEquals("door,smart", e.labels)
        assertEquals("HOME", e.icon)
        assertEquals(meta, Library.metaOf(e))
        assertEquals("https://example.com", Library.records(e).single().uri)
    }

    @Test
    fun `meta validation`() {
        assertNull(meta.validate())
        assertEquals(LibraryMeta.Problem.NAME_EMPTY, meta.copy(name = " ").validate())
        assertEquals(LibraryMeta.Problem.LABELS_INVALID, meta.copy(labels = listOf("a,b")).validate())
        assertEquals(LibraryMeta.Problem.TEXT_TOO_LONG, meta.copy(notes = "x".repeat(4001)).validate())
    }

    @Test
    fun `compare reports field and record differences`() {
        val a = Library.entityFrom(snap, meta, 1)
        val other = NdefMessageCodec.encode(listOf(RecordSpec.Text("hi").toRawRecord()))
        val b = a.copy(payloadHex = other.toHex(), capacityBytes = 504)
        val diffs = Library.compare(a, b).map { it.field }
        assertTrue(Difference.Field.CAPACITY in diffs)
        assertTrue(Difference.Field.RECORD in diffs)
        assertEquals(emptyList<Difference>(), Library.compare(a, a))
    }

    @Test
    fun `csv quotes and neutralises formulas`() {
        assertEquals("\"a,b\"", Library.csv("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Library.csv("say \"hi\""))
        assertEquals("'=HYPERLINK(1)", Library.csv("=HYPERLINK(1)"))
        val csv = Library.toCsv(listOf(Library.entityFrom(snap, meta.copy(name = "=cmd"), 1)))
        assertTrue(csv.lines()[1].startsWith("'=cmd,"))
    }

    @Test
    fun `library survives export import with validation`() {
        val e = Library.entityFrom(snap, meta, 1_000)
        val text = ExportCodec.encode(emptyList(), emptyList(), 2_000, library = listOf(e))
        val b = (ExportCodec.decode(text, 2_000, false, false) as ImportResult.Ok).bundle
        assertEquals(listOf(e), b.library)
        val bad = text.replace("\"Front door\"", "\"\"")
        assertEquals(1, (ExportCodec.decode(bad, 2_000, false, false) as ImportResult.Ok).bundle.skippedLibrary)
    }
}
