package dev.nfcstudio.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.ui.graphics.vector.ImageVector
import dev.nfcstudio.app.R
import dev.nfcstudio.app.core.ndef.DecodedRecord
import dev.nfcstudio.app.core.ndef.Detail
import dev.nfcstudio.app.core.ndef.RecordKind

/** Icon for a decoded record, refining URIs by scheme. */
fun recordIcon(r: DecodedRecord): ImageVector = when (r.kind) {
    RecordKind.TEXT -> Icons.Filled.TextFields
    RecordKind.URI -> uriIcon(r.uri)
    RecordKind.SMART_POSTER -> Icons.Filled.Campaign
    RecordKind.MIME -> Icons.Filled.DataObject
    RecordKind.CONTACT -> Icons.Filled.ContactPage
    RecordKind.WIFI -> Icons.Filled.Wifi
    RecordKind.BLUETOOTH -> Icons.Filled.Bluetooth
    RecordKind.APP -> Icons.Filled.Android
    RecordKind.TASK -> Icons.Filled.Bolt
    RecordKind.EXTERNAL -> Icons.Filled.Extension
    RecordKind.EMPTY -> Icons.Filled.Remove
    RecordKind.UNKNOWN -> Icons.Filled.HelpOutline
}

fun uriIcon(uri: String?): ImageVector = when (uri?.substringBefore(':')?.lowercase()) {
    "tel" -> Icons.Filled.Phone
    "sms", "smsto" -> Icons.Filled.Sms
    "mailto" -> Icons.Filled.Email
    "geo" -> Icons.Filled.LocationOn
    else -> Icons.Filled.Link
}

fun recordKindLabel(kind: RecordKind): Int = when (kind) {
    RecordKind.TEXT -> R.string.record_text
    RecordKind.URI -> R.string.record_uri
    RecordKind.SMART_POSTER -> R.string.record_smart_poster
    RecordKind.MIME -> R.string.record_mime
    RecordKind.CONTACT -> R.string.record_contact
    RecordKind.WIFI -> R.string.record_wifi
    RecordKind.BLUETOOTH -> R.string.record_bluetooth
    RecordKind.APP -> R.string.record_app
    RecordKind.TASK -> R.string.record_task
    RecordKind.EXTERNAL -> R.string.record_external
    RecordKind.EMPTY -> R.string.record_empty
    RecordKind.UNKNOWN -> R.string.record_unknown
}

fun detailLabel(label: Detail.Label): Int = when (label) {
    Detail.Label.NAME -> R.string.detail_name
    Detail.Label.PHONE -> R.string.detail_phone
    Detail.Label.EMAIL -> R.string.detail_email
    Detail.Label.ORGANIZATION -> R.string.detail_organization
    Detail.Label.ADDRESS -> R.string.detail_address
    Detail.Label.URL -> R.string.detail_url
    Detail.Label.TITLE -> R.string.detail_title
    Detail.Label.SSID -> R.string.detail_ssid
    Detail.Label.AUTHENTICATION -> R.string.detail_auth
    Detail.Label.ENCRYPTION -> R.string.detail_encryption
    Detail.Label.NETWORK_KEY -> R.string.detail_network_key
    Detail.Label.MAC -> R.string.detail_mac
    Detail.Label.DEVICE_NAME -> R.string.detail_device_name
    Detail.Label.PACKAGE -> R.string.detail_package
    Detail.Label.TOKEN -> R.string.detail_token
}

/** "org.nfcforum.ndef.type2" → "NFC Forum Type 2". */
fun ndefTypeName(type: String?): String? = when (type) {
    null -> null
    "org.nfcforum.ndef.type1" -> "NFC Forum Type 1"
    "org.nfcforum.ndef.type2" -> "NFC Forum Type 2"
    "org.nfcforum.ndef.type3" -> "NFC Forum Type 3"
    "org.nfcforum.ndef.type4" -> "NFC Forum Type 4"
    "com.nxp.ndef.mifareclassic" -> "MIFARE Classic (NXP NDEF)"
    "com.nxp.ndef.icodesli" -> "NFC Forum Type 5 (ICODE SLI)"
    else -> type
}
