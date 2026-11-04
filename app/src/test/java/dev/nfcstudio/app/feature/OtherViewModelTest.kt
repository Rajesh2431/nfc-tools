package dev.nfcstudio.app.feature

import dev.nfcstudio.app.core.common.hexToBytesOrNull
import dev.nfcstudio.app.core.ndef.NdefMessageCodec
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.toRawRecord
import dev.nfcstudio.app.core.nfc.ChipParsing
import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.common.toHex
import dev.nfcstudio.app.feature.other.OtherViewModel
import dev.nfcstudio.app.feature.other.OtherViewModel.Op
import dev.nfcstudio.app.feature.other.OtherViewModel.OtherError
import dev.nfcstudio.app.feature.other.OtherViewModel.Phase
import dev.nfcstudio.app.testing.FakeTagGateway
import dev.nfcstudio.app.testing.MainDispatcherRule
import dev.nfcstudio.app.testing.TestTag
import dev.nfcstudio.app.testing.formatableTag
import dev.nfcstudio.app.testing.ndefTag
import dev.nfcstudio.app.testing.recordsContent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OtherViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private fun vm(g: FakeTagGateway) = OtherViewModel(g, io = main.dispatcher)

    private val message = NdefMessageCodec.encode(listOf(RecordSpec.Uri("https://example.com").toRawRecord()))
    private val source = ndefTag(content = recordsContent(RecordSpec.Uri("https://example.com")).copy(rawMessageHex = message.toHex(), sizeBytes = message.size))

    @Test
    fun `copy reads source then writes exact bytes to an empty target`() {
        val g = FakeTagGateway(NfcResult.Ok(ndefTag()))
        g.readQueue += NfcResult.Ok(source)
        g.readQueue += NfcResult.Ok(ndefTag().copy(uidHex = "04ffffff"))
        val vm = vm(g)
        vm.start(Op.COPY); vm.onTag(TestTag)
        assertEquals(Phase.Awaiting(Op.COPY, copyTarget = true, copiedBytes = message.size), vm.phase.value)
        vm.onTag(TestTag)
        assertArrayEquals(message, g.rawWrites.single())
        assertTrue(vm.phase.value is Phase.Done)
    }

    @Test
    fun `copy refuses the same tag and asks before overwriting`() {
        val g = FakeTagGateway(NfcResult.Ok(source))
        val vm = vm(g)
        vm.start(Op.COPY); vm.onTag(TestTag); vm.onTag(TestTag) // same UID
        assertEquals(OtherError.SameTag, vm.prepareError.value)
        assertTrue(g.rawWrites.isEmpty())
        g.readQueue += NfcResult.Ok(source.copy(uidHex = "04eeeeee"))
        vm.onTag(TestTag)
        assertTrue(vm.phase.value is Phase.Confirm)
        assertTrue(g.rawWrites.isEmpty())
        vm.confirm()
        assertEquals(1, g.rawWrites.size)
    }

    @Test
    fun `copy of an empty source fails truthfully`() {
        val vm = vm(FakeTagGateway(NfcResult.Ok(ndefTag())))
        vm.start(Op.COPY); vm.onTag(TestTag)
        assertEquals(Phase.Failed(Op.COPY, OtherError.NothingToCopy), vm.phase.value)
    }

    @Test
    fun `erase and lock always confirm, lock needs intent first`() {
        val g = FakeTagGateway(NfcResult.Ok(ndefTag(content = NdefContent.Malformed)))
        val vm = vm(g)
        vm.start(Op.ERASE); vm.onTag(TestTag)
        assertTrue(vm.phase.value is Phase.Confirm)
        vm.confirm()
        assertEquals(listOf(listOf(RecordSpec.Empty)), g.writes)
        vm.dismissResult()
        vm.start(Op.LOCK)
        vm.onTag(TestTag) // not armed during the intent step
        assertEquals(Phase.Prepare(Op.LOCK), vm.phase.value)
        vm.confirmLockIntent(); vm.onTag(TestTag)
        assertTrue(vm.phase.value is Phase.Confirm)
        assertEquals(0, g.locks)
        vm.confirm()
        assertEquals(1, g.locks)
    }

    @Test
    fun `format only blank formatable tags`() {
        val already = vm(FakeTagGateway(NfcResult.Ok(ndefTag())))
        already.start(Op.FORMAT); already.onTag(TestTag)
        assertEquals(Phase.Failed(Op.FORMAT, OtherError.AlreadyFormatted), already.phase.value)
        val g = FakeTagGateway(NfcResult.Ok(formatableTag()))
        val vm = vm(g)
        vm.start(Op.FORMAT); vm.onTag(TestTag); vm.confirm()
        assertEquals(listOf(listOf(RecordSpec.Empty)), g.formats)
    }

    @Test
    fun `set password validates entry, requires identified ntag and confirmation`() {
        val ntag = ChipParsing.parseNxpVersion("0004040201001103".hexToBytesOrNull()!!)
        val g = FakeTagGateway(NfcResult.Ok(ndefTag().copy(chip = ntag)))
        val vm = vm(g)
        vm.start(Op.SET_PASSWORD)
        vm.submitPassword("abcd", "abce", false)
        assertEquals(OtherError.PasswordMismatch, vm.prepareError.value)
        vm.submitPassword("ab", "ab", false)
        assertEquals(OtherError.PasswordLength, vm.prepareError.value)
        vm.submitPassword("abcd1234", "abcd1234", true)
        vm.onTag(TestTag)
        assertTrue(g.identifyRequested)
        assertTrue(vm.phase.value is Phase.Confirm)
        assertTrue(g.passwords.isEmpty())
        vm.confirm()
        assertEquals(listOf("abcd1234" to true), g.passwords)
    }

    @Test
    fun `set password refuses unidentified chips`() {
        val vm = vm(FakeTagGateway(NfcResult.Ok(ndefTag())))
        vm.start(Op.SET_PASSWORD); vm.submitPassword("abcd1234", "abcd1234", false); vm.onTag(TestTag)
        assertEquals(Phase.Failed(Op.SET_PASSWORD, OtherError.Nfc(NfcError.ChipUnsupported)), vm.phase.value)
    }

    @Test
    fun `remove password and memory dump surface results`() {
        val g = FakeTagGateway(NfcResult.Ok(ndefTag()), passwordResult = NfcResult.Err(NfcError.WrongPassword))
        val vm = vm(g)
        vm.start(Op.REMOVE_PASSWORD); vm.submitPassword("wrong123", null, false); vm.onTag(TestTag)
        assertEquals(Phase.Failed(Op.REMOVE_PASSWORD, OtherError.Nfc(NfcError.WrongPassword)), vm.phase.value)
        vm.dismissResult()
        vm.start(Op.READ_MEMORY); vm.onTag(TestTag)
        assertEquals(listOf("04a1b2c3"), (vm.phase.value as Phase.Done).dump!!.units)
    }
}
