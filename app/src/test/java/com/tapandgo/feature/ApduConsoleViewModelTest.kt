package com.tapandgo.feature

import com.tapandgo.core.apdu.CommandApdu
import com.tapandgo.core.nfc.ApduChannel
import com.tapandgo.core.nfc.NfcError
import com.tapandgo.core.nfc.NfcResult
import com.tapandgo.feature.advanced.ApduConsoleViewModel
import com.tapandgo.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ApduConsoleViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeChannel(var reply: NfcResult<ByteArray>) : ApduChannel {
        val sent = mutableListOf<ByteArray>()
        var lastTimeout = 0
        var closed = false
        override val maxTransceiveLength = 261
        override val extendedLengthSupported = false
        override fun transceive(command: ByteArray, timeoutMs: Int): NfcResult<ByteArray> {
            sent += command; lastTimeout = timeoutMs; return reply
        }
        override fun close() { closed = true }
    }

    @Test
    fun `valid read command is sent and response decoded`() {
        val ch = FakeChannel(NfcResult.Ok(byteArrayOf(0x6F, 0x90.toByte(), 0x00)))
        val vm = ApduConsoleViewModel(ch, allowStateChanging = false, io = main.dispatcher, clock = { 0 })
        vm.setTimeout(99_999)
        vm.setInput("00A4040007D2760000850101")
        vm.send()
        assertEquals(1, ch.sent.size)
        assertEquals(5_000, ch.lastTimeout)
        val e = vm.state.value.transcript.single() as ApduConsoleViewModel.Entry.Response
        assertEquals(0x9000, e.response.sw)
    }

    @Test
    fun `state changing commands are blocked unless allowed`() {
        val ch = FakeChannel(NfcResult.Ok(byteArrayOf(0x90.toByte(), 0x00)))
        val vm = ApduConsoleViewModel(ch, allowStateChanging = false, io = main.dispatcher)
        vm.setInput("00D6000001AA"); vm.send()
        assertTrue(vm.state.value.blocked)
        assertTrue(ch.sent.isEmpty())
        val unlocked = ApduConsoleViewModel(ch, allowStateChanging = true, io = main.dispatcher)
        unlocked.setInput("00D6000001AA"); unlocked.send()
        assertEquals(1, ch.sent.size)
    }

    @Test
    fun `invalid input never reaches the card and io errors are typed`() {
        val ch = FakeChannel(NfcResult.Err(NfcError.TagLost))
        val vm = ApduConsoleViewModel(ch, allowStateChanging = false, io = main.dispatcher)
        vm.setInput("00A4"); vm.send()
        assertEquals(CommandApdu.Problem.TOO_SHORT, vm.state.value.problem)
        assertTrue(ch.sent.isEmpty())
        vm.setInput("00B0000010"); vm.send()
        assertEquals(NfcError.TagLost, (vm.state.value.transcript.single() as ApduConsoleViewModel.Entry.Failure).error)
    }
}
