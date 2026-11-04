package dev.nfcstudio.app.feature.advanced

import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NfcState
import dev.nfcstudio.app.core.nfc.TagSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Diagnostics export (spec 4.6): device/API level, exposed technologies,
 * protocol metadata and error codes. Payloads and the UID are never included.
 */
@Serializable
data class Diagnostics(
    val format: String = "nfc-studio-diagnostics",
    val schemaVersion: Int = 1,
    val appVersion: String,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val nfcState: String,
    val technologies: List<String>,
    val techDetails: Map<String, Map<String, String>>,
    val ndefType: String? = null,
    val ndefCapacity: Int? = null,
    val ndefWritable: Boolean? = null,
    val ndefCanMakeReadOnly: Boolean? = null,
    val ndefContent: String? = null,
    val ndefRecordCount: Int? = null,
    val ndefFromCache: Boolean? = null,
    val recentErrorCodes: Map<String, Int>,
)

object DiagnosticsBuilder {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun build(
        snapshot: TagSnapshot,
        nfcState: NfcState,
        appVersion: String,
        manufacturer: String,
        model: String,
        sdkInt: Int,
        errorCodes: List<String>,
    ): Diagnostics {
        val ndef = snapshot.ndef
        return Diagnostics(
            appVersion = appVersion,
            manufacturer = manufacturer,
            model = model,
            sdkInt = sdkInt,
            nfcState = nfcState.name,
            technologies = snapshot.technologies,
            techDetails = snapshot.techDetails.associate { d -> d.technology to d.properties.toMap() },
            ndefType = ndef?.ndefType,
            ndefCapacity = ndef?.maxCapacityBytes,
            ndefWritable = ndef?.isWritable,
            ndefCanMakeReadOnly = ndef?.canMakeReadOnly,
            ndefContent = ndef?.content?.let {
                when (it) {
                    NdefContent.Empty -> "EMPTY"
                    NdefContent.Malformed -> "MALFORMED"
                    NdefContent.Unreadable -> "UNREADABLE"
                    is NdefContent.Records -> "RECORDS"
                }
            },
            ndefRecordCount = (ndef?.content as? NdefContent.Records)?.records?.size,
            ndefFromCache = ndef?.fromCache,
            recentErrorCodes = errorCodes.filter { it != "OK" }.groupingBy { it }.eachCount(),
        )
    }

    fun encode(d: Diagnostics): String = json.encodeToString(Diagnostics.serializer(), d)
}
