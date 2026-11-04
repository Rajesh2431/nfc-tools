package dev.nfcstudio.app.feature

import dev.nfcstudio.app.core.ndef.RecordProblem
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.nfc.ContentSummary
import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.nfc.Verification
import dev.nfcstudio.app.feature.writer.WriteViewModel
import dev.nfcstudio.app.feature.writer.WriteViewModel.Operation
import dev.nfcstudio.app.feature.writer.WriteViewModel.Phase
import dev.nfcstudio.app.testing.FakeTagGateway
import dev.nfcstudio.app.testing.MainDispatcherRule
import dev.nfcstudio.app.testing.TestTag
import dev.nfcstudio.app.testing.formatableTag
import dev.nfcstudio.app.testing.ndefTag
import dev.nfcstudio.app.testing.plainTag
import dev.nfcstudio.app.testing.recordsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WriteViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun vm(gateway: FakeTagGateway) = WriteViewModel(gateway, io = main.dispatcher).apply {
        updateDraft { it.copy(text = "hello") }
        addDraftRecord()
    }

    @Test
    fun `invalid language is rejected at entry and never encoded`() {
        val vm = WriteViewModel(FakeTagGateway(NfcResult.Ok(ndefTag())), io = main.dispatcher)
        vm.updateDraft { it.copy(text = "x", language = "e n") }
        assertFalse(vm.addDraftRecord())
        assertEquals(RecordProblem.LANGUAGE_INVALID, vm.draftError.value)
        assertTrue(vm.records.value.isEmpty())
    }

    @Test
    fun `tags are ignored while editing`() {
        val gateway = FakeTagGateway(NfcResult.Ok(ndefTag()))
        val vm = vm(gateway)
        vm.onTag(TestTag)
        assertEquals(Phase.Editing, vm.phase.value)
        assertTrue(gateway.writes.isEmpty())
    }

    @Test
    fun `empty tag is written without confirmation and verified`() {
        val gateway = FakeTagGateway(NfcResult.Ok(ndefTag()))
        val vm = vm(gateway)
        vm.startWrite()
        vm.onTag(TestTag)
        assertEquals(listOf(listOf(RecordSpec.Text("hello", "en"))), gateway.writes)
        val phase = vm.phase.value as Phase.Success
        assertEquals(Verification.VERIFIED, phase.verification)
        assertEquals(vm.estimatedSize.value, phase.bytes)
    }

    @Test
    fun `existing content requires overwrite confirmation`() {
        val gateway = FakeTagGateway(NfcResult.Ok(ndefTag(content = recordsContent(RecordSpec.Uri("https://a.example")))))
        val vm = vm(gateway)
        vm.startWrite()
        vm.onTag(TestTag)
        val confirm = vm.phase.value as Phase.ConfirmOverwrite
        assertEquals(ContentSummary.Kind.RECORDS, confirm.existing.kind)
        assertTrue(gateway.writes.isEmpty())
        vm.confirm()
        assertEquals(1, gateway.writes.size)
    }

    @Test
    fun `malformed and unreadable content also require confirmation`() {
        for (content in listOf(NdefContent.Malformed, NdefContent.Unreadable)) {
            val gateway = FakeTagGateway(NfcResult.Ok(ndefTag(content = content)))
            val vm = vm(gateway)
            vm.startWrite()
            vm.onTag(TestTag)
            assertTrue(vm.phase.value is Phase.ConfirmOverwrite)
            assertTrue(gateway.writes.isEmpty())
        }
    }

    @Test
    fun `declining overwrite writes nothing and waits for another tag`() {
        val gateway = FakeTagGateway(NfcResult.Ok(ndefTag(content = NdefContent.Malformed)))
        val vm = vm(gateway)
        vm.startWrite()
        vm.onTag(TestTag)
        vm.declineConfirmation()
        assertEquals(Phase.AwaitingTag(Operation.WRITE), vm.phase.value)
        vm.confirm() // no pending tag any more
        assertTrue(gateway.writes.isEmpty())
    }

    @Test
    fun `read-only and too-small tags fail before writing`() {
        val ro = FakeTagGateway(NfcResult.Ok(ndefTag(writable = false)))
        vm(ro).apply { startWrite(); onTag(TestTag) }.also {
            assertEquals(Phase.Failed(NfcError.ReadOnly), it.phase.value)
        }
        val small = FakeTagGateway(NfcResult.Ok(ndefTag(capacity = 4)))
        val vm = vm(small).apply { startWrite(); onTag(TestTag) }
        assertTrue((vm.phase.value as Phase.Failed).error is NfcError.TooLarge)
        assertTrue(ro.writes.isEmpty() && small.writes.isEmpty())
    }

    @Test
    fun `blank formatable tag needs format confirmation`() {
        val gateway = FakeTagGateway(NfcResult.Ok(formatableTag()))
        val vm = vm(gateway)
        vm.startWrite()
        vm.onTag(TestTag)
        assertEquals(Phase.ConfirmFormat, vm.phase.value)
        assertTrue(gateway.formats.isEmpty())
        vm.confirm()
        assertEquals(1, gateway.formats.size)
        assertEquals(Verification.NOT_POSSIBLE, (vm.phase.value as Phase.Success).verification)
    }

    @Test
    fun `non ndef tag reports no ndef support`() {
        val vm = vm(FakeTagGateway(NfcResult.Ok(plainTag()))).apply { startWrite(); onTag(TestTag) }
        assertEquals(Phase.Failed(NfcError.NoNdefSupport), vm.phase.value)
    }

    @Test
    fun `verify failure and tag loss surface as typed failures`() {
        val gateway = FakeTagGateway(NfcResult.Ok(ndefTag()), writeResult = NfcResult.Err(NfcError.VerifyFailed))
        val vm = vm(gateway).apply { startWrite(); onTag(TestTag) }
        assertEquals(Phase.Failed(NfcError.VerifyFailed), vm.phase.value)
        val lost = vm(FakeTagGateway(NfcResult.Err(NfcError.TagLost))).apply { startWrite(); onTag(TestTag) }
        assertEquals(Phase.Failed(NfcError.TagLost), lost.phase.value)
    }

    @Test
    fun `erase always asks and writes a single empty record`() {
        val gateway = FakeTagGateway(NfcResult.Ok(ndefTag(content = recordsContent(RecordSpec.Text("old")))))
        val vm = vm(gateway)
        vm.startErase()
        vm.onTag(TestTag)
        assertTrue(vm.phase.value is Phase.ConfirmErase)
        vm.confirm()
        assertEquals(listOf(listOf(RecordSpec.Empty)), gateway.writes)
    }

    @Test
    fun `lock needs two confirmations`() {
        val gateway = FakeTagGateway(NfcResult.Ok(ndefTag()))
        val vm = vm(gateway)
        vm.requestLock()
        assertEquals(Phase.ConfirmLockIntent, vm.phase.value)
        vm.onTag(TestTag) // not armed yet
        assertEquals(0, gateway.locks)
        vm.confirmLockIntent()
        vm.onTag(TestTag)
        assertTrue(vm.phase.value is Phase.ConfirmLock)
        assertEquals(0, gateway.locks)
        vm.confirm()
        assertEquals(1, gateway.locks)
        assertEquals(Operation.LOCK, (vm.phase.value as Phase.Success).operation)
    }

    @Test
    fun `lock is refused when the platform cannot lock`() {
        val vm = vm(FakeTagGateway(NfcResult.Ok(ndefTag(canLock = false))))
        vm.requestLock(); vm.confirmLockIntent(); vm.onTag(TestTag)
        assertEquals(Phase.Failed(NfcError.LockUnsupported), vm.phase.value)
    }

    @Test
    fun `records can be reordered and replaced with invalid entries dropped`() {
        val vm = vm(FakeTagGateway(NfcResult.Ok(ndefTag())))
        vm.updateDraft { it.copy(kind = WriteViewModel.DraftKind.URI, uri = "https://example.com") }
        vm.addDraftRecord()
        vm.moveRecord(1, -1)
        assertEquals(RecordSpec.Uri("https://example.com"), vm.records.value.first())
        val dropped = vm.replaceRecords(listOf(RecordSpec.Text("ok"), RecordSpec.Uri("javascript:x")))
        assertEquals(1, dropped)
        assertEquals(listOf<RecordSpec>(RecordSpec.Text("ok")), vm.records.value)
    }
}
