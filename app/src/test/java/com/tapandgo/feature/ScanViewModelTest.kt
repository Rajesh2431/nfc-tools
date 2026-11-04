package com.tapandgo.feature

import com.tapandgo.core.nfc.NfcError
import com.tapandgo.core.nfc.NfcResult
import com.tapandgo.core.nfc.TagSnapshot
import com.tapandgo.feature.scanner.ScanViewModel
import com.tapandgo.testing.FakeTagGateway
import com.tapandgo.testing.MainDispatcherRule
import com.tapandgo.testing.TestTag
import com.tapandgo.testing.ndefTag
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
