package dev.nfcstudio.app.core.nfc

import android.nfc.tech.IsoDep

/**
 * One ISO-DEP connection kept open across console commands (closing would
 * reset the card's selection state). Always closed by [close], which callers
 * invoke when leaving the console (AGENTS rule 5).
 */
/** What the APDU console needs; implemented by [IsoDepSession], faked in tests. */
interface ApduChannel : AutoCloseable {
    val maxTransceiveLength: Int
    val extendedLengthSupported: Boolean
    fun transceive(command: ByteArray, timeoutMs: Int): NfcResult<ByteArray>
}

class IsoDepSession(private val tag: DiscoveredTag) : ApduChannel {
    private var isoDep: IsoDep? = null

    override val maxTransceiveLength: Int
        get() = runCatching { IsoDep.get((tag as PlatformTag).tag)?.maxTransceiveLength }.getOrNull() ?: 261

    override val extendedLengthSupported: Boolean
        get() = runCatching { IsoDep.get((tag as PlatformTag).tag)?.isExtendedLengthApduSupported }.getOrNull() == true

    override fun transceive(command: ByteArray, timeoutMs: Int): NfcResult<ByteArray> = nfcCatching {
        val iso = isoDep ?: (IsoDep.get((tag as PlatformTag).tag) ?: return NfcResult.Err(NfcError.TechnologyUnavailable("IsoDep")))
            .also { isoDep = it }
        if (!iso.isConnected) iso.connect()
        iso.timeout = timeoutMs.coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)
        NfcResult.Ok(iso.transceive(command))
    }

    override fun close() {
        runCatching { isoDep?.close() }
        isoDep = null
    }

    companion object {
        const val MIN_TIMEOUT = 100
        const val MAX_TIMEOUT = 5_000
    }
}
