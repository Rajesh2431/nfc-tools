package com.tapandgo.core.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the platform-independent NFC state mapping used by the UI.
 */
class NfcAvailabilityTest {

    @Test
    fun `adapter absent maps to NOT_AVAILABLE`() {
        assertEquals(NfcState.NOT_AVAILABLE, NfcAvailability.stateOf(hasAdapter = false, isAdapterEnabled = false))
    }

    @Test
    fun `adapter present but disabled maps to DISABLED`() {
        assertEquals(NfcState.DISABLED, NfcAvailability.stateOf(hasAdapter = true, isAdapterEnabled = false))
    }

    @Test
    fun `adapter present and enabled maps to READY`() {
        assertEquals(NfcState.READY, NfcAvailability.stateOf(hasAdapter = true, isAdapterEnabled = true))
    }

    @Test
    fun `enabled flag is ignored without adapter`() {
        assertEquals(NfcState.NOT_AVAILABLE, NfcAvailability.stateOf(hasAdapter = false, isAdapterEnabled = true))
    }
}
