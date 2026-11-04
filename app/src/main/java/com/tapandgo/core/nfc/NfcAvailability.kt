package com.tapandgo.core.nfc

import android.content.Context
import android.nfc.NfcAdapter

/**
 * Truthful NFC hardware states as exposed by the platform adapter.
 * Values map 1:1 to the states demanded by the specification:
 * no adapter, adapter present but disabled, adapter present and enabled.
 */
enum class NfcState {
    /** Device has an NFC adapter and it is enabled. */
    READY,

    /** Device has an NFC adapter, but the user has turned NFC off. */
    DISABLED,

    /** Device has no NFC hardware at all. */
    NOT_AVAILABLE,
}

/**
 * Pure mapping of platform facts to a [NfcState], kept free of Android
 * dependencies so it can be unit tested on the JVM.
 */
object NfcAvailability {

    /**
     * @param hasAdapter true if [NfcAdapter.getDefaultAdapter] returned an adapter
     * @param isAdapterEnabled true if the adapter reports itself enabled
     */
    fun stateOf(hasAdapter: Boolean, isAdapterEnabled: Boolean): NfcState = when {
        !hasAdapter -> NfcState.NOT_AVAILABLE
        isAdapterEnabled -> NfcState.READY
        else -> NfcState.DISABLED
    }

    /** Reads the current platform NFC state for [context] (application context ok). */
    fun current(context: Context): NfcState {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return stateOf(hasAdapter = adapter != null, isAdapterEnabled = adapter?.isEnabled == true)
    }
}
