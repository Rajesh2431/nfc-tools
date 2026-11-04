package dev.nfcstudio.app.feature

import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.nfc.TagSnapshot
import dev.nfcstudio.app.feature.scanner.ScanViewModel
import dev.nfcstudio.app.testing.FakeTagGateway
import dev.nfcstudio.app.testing.MainDispatcherRule
import dev.nfcstudio.app.testing.TestTag
import dev.nfcstudio.app.testing.ndefTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ScanViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `successful scan exposes snapshot and reports it`() {
        val recorded = mutableListOf<NfcResult<TagSnapshot>>()
        val vm = ScanViewModel(FakeTagGateway(NfcResult.Ok(ndefTag())), main.dispatcher, onScanned = { recorded += it })
        vm.onTag(TestTag)
        assertEquals(ndefTag(), vm.state.value.snapshot)
        assertEquals(1, vm.state.value.scanCount)
        assertEquals(1, recorded.size)
    }

    @Test
    fun `failed scan exposes typed error and clear resets`() {
        val vm = ScanViewModel(FakeTagGateway(NfcResult.Err(NfcError.TagLost)), main.dispatcher)
        vm.onTag(TestTag)
        assertEquals(NfcError.TagLost, vm.state.value.error)
        vm.clear()
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.snapshot)
    }
}
