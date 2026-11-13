package com.tapandgo.nfc.hce

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.tapandgo.billing.EntitlementRepository
import com.tapandgo.billing.PremiumFeature
import com.tapandgo.core.common.toHex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One APDU exchange. Only the header and status word are kept; payload bytes are counted, not stored. */
data class ApduLogEntry(val time: Long, val command: String, val status: String)

/**
 * Emulation profile (the NDEF message), on/off state and the session log.
 * "On" is the real component state of [NdefHceService], so the system only
 * routes the NDEF AID to this app while the user has emulation enabled.
 */
@Singleton
class EmulationStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlements: EntitlementRepository,
) {
    private val file get() = File(context.filesDir, "hce/message.bin")
    @Volatile private var cached: ByteArray? = null
    private val _log = MutableStateFlow<List<ApduLogEntry>>(emptyList())
    val log: StateFlow<List<ApduLogEntry>> = _log.asStateFlow()
    private val _enabled = MutableStateFlow(isComponentEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val hceSupported: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

    fun message(): ByteArray? = cached ?: runCatching { file.takeIf { it.exists() }?.readBytes() }.getOrNull()?.also { cached = it }

    fun save(message: ByteArray): Boolean {
        if (message.isEmpty() || message.size > Type4Emulator.MAX_MESSAGE) return false
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(message)
            cached = message
        }.isSuccess
    }

    private val component get() = ComponentName(context, NdefHceService::class.java)

    private fun isComponentEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Enables/disables the HostApduService; refuses to enable without a message or Premium. */
    suspend fun setEnabled(on: Boolean): Boolean {
        if (on && (message() == null || !hceSupported || !entitlements.current().allows(PremiumFeature.NDEF_EMULATION))) return false
        context.packageManager.setComponentEnabledSetting(
            component,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        _enabled.value = isComponentEnabled()
        return _enabled.value == on
    }

    /** Called on the service's thread; bounded wait for the (cached) DataStore read. */
    fun allowedBlocking(): Boolean = runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(500) { entitlements.current().allows(PremiumFeature.NDEF_EMULATION) } ?: false
    }

    fun record(apdu: ByteArray, response: ByteArray) {
        val entry = ApduLogEntry(System.currentTimeMillis(), describe(apdu), response.takeLast(2).toByteArray().toHex())
        _log.update { (it + entry).takeLast(200) }
    }

    fun clearLog() { _log.value = emptyList() }

    companion object {
        fun describe(apdu: ByteArray): String {
            if (apdu.size < 4) return "short APDU (${apdu.size} B)"
            val header = apdu.copyOfRange(0, 4).toHex()
            val name = when (apdu[1].toInt() and 0xFF) {
                0xA4 -> if (apdu[2].toInt() == 0x04) "SELECT AID" else "SELECT FILE"
                0xB0 -> "READ BINARY"
                0xD6 -> "UPDATE BINARY"
                else -> "INS %02X".format(apdu[1])
            }
            return "$name [$header] ${apdu.size - 4} B"
        }
    }
}
