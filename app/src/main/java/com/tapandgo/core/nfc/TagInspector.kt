package com.tapandgo.core.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.tapandgo.core.common.toHex

/**
 * Collects per-technology metadata that Android exposes without RF I/O
 * (spec 4.6). Only values the platform actually reports are listed; a
 * technology name never implies access to protected data.
 */
object TagInspector {

    fun inspect(tag: Tag): List<TechDetail> = buildList {
        NfcA.get(tag)?.let { t ->
            add(
                TechDetail(
                    "NfcA (ISO 14443-3A)",
                    listOfNotNull(
                        "ATQA" to t.atqa.toHex().uppercase(),
                        "SAK" to "%02X".format(t.sak.toInt() and 0xFF),
                        maxTransceive(t.maxTransceiveLength),
                    ),
                ),
            )
        }
        NfcB.get(tag)?.let { t ->
            add(
                TechDetail(
                    "NfcB (ISO 14443-3B)",
                    listOfNotNull(
                        "Application data" to t.applicationData.toHex().uppercase(),
                        "Protocol info" to t.protocolInfo.toHex().uppercase(),
                        maxTransceive(t.maxTransceiveLength),
                    ),
                ),
            )
        }
        NfcF.get(tag)?.let { t ->
            add(
                TechDetail(
                    "NfcF (JIS 6319-4)",
                    listOfNotNull(
                        "Manufacturer" to t.manufacturer.toHex().uppercase(),
                        "System code" to t.systemCode.toHex().uppercase(),
                        maxTransceive(t.maxTransceiveLength),
                    ),
                ),
            )
        }
        NfcV.get(tag)?.let { t ->
            add(
                TechDetail(
                    "NfcV (ISO 15693)",
                    listOfNotNull(
                        "DSFID" to "%02X".format(t.dsfId.toInt() and 0xFF),
                        "Response flags" to "%02X".format(t.responseFlags.toInt() and 0xFF),
                        maxTransceive(t.maxTransceiveLength),
                    ),
                ),
            )
        }
        IsoDep.get(tag)?.let { t ->
            add(
                TechDetail(
                    "IsoDep (ISO 14443-4)",
                    listOfNotNull(
                        t.historicalBytes?.let { "Historical bytes" to it.toHex().uppercase() },
                        t.hiLayerResponse?.let { "Hi-layer response" to it.toHex().uppercase() },
                        runCatching { "Extended-length APDUs" to t.isExtendedLengthApduSupported.toString() }.getOrNull(),
                        maxTransceive(t.maxTransceiveLength),
                    ),
                ),
            )
        }
        // MifareClassic is only exposed on devices with NXP controllers.
        runCatching { MifareClassic.get(tag) }.getOrNull()?.let { t ->
            add(
                TechDetail(
                    "MifareClassic",
                    listOf(
                        "Type" to when (t.type) {
                            MifareClassic.TYPE_CLASSIC -> "Classic"
                            MifareClassic.TYPE_PLUS -> "Plus"
                            MifareClassic.TYPE_PRO -> "Pro"
                            else -> "unknown"
                        },
                        "Size" to "${t.size} bytes",
                        "Sectors / blocks" to "${t.sectorCount} / ${t.blockCount}",
                    ),
                ),
            )
        }
        runCatching { MifareUltralight.get(tag) }.getOrNull()?.let { t ->
            add(
                TechDetail(
                    "MifareUltralight",
                    listOf(
                        "Type" to when (t.type) {
                            MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
                            MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                            else -> "unknown"
                        },
                    ),
                ),
            )
        }
    }

    private fun maxTransceive(length: Int): Pair<String, String>? =
        length.takeIf { it > 0 }?.let { "Max transceive" to "$it bytes" }
}
