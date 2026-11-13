package com.tapandgo.nfc.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Host card emulation of a read-only NFC Forum Type 4 NDEF tag. Registered
 * only for the NDEF application AID (D2760000850101); disabled in the
 * manifest and enabled by the user from the Emulation screen.
 */
class NdefHceService : HostApduService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun emulation(): EmulationStore
    }

    private val store by lazy { EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).emulation() }
    private var emulator: Type4Emulator? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val response = respond(commandApdu)
        store.record(commandApdu, response)
        return response
    }

    private fun respond(apdu: ByteArray): ByteArray {
        val emu = emulator ?: run {
            val message = store.message() ?: return Type4Emulator.SW_NOT_FOUND
            if (!store.allowedBlocking()) return Type4Emulator.SW_NOT_FOUND
            runCatching { Type4Emulator(message) }.getOrNull()?.also { emulator = it } ?: return Type4Emulator.SW_NOT_FOUND
        }
        return emu.process(apdu)
    }

    override fun onDeactivated(reason: Int) {
        emulator = null
    }
}
