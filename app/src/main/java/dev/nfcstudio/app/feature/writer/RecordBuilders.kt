package dev.nfcstudio.app.feature.writer

import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.RecordValidator
import dev.nfcstudio.app.core.ndef.RichRecords

/** Record types offered by the Write tab, in display order. */
enum class RecordType { TEXT, URL, PHONE, SMS, EMAIL, CONTACT, LOCATION, ADDRESS, WIFI, BLUETOOTH, APP, MIME }

/** Form field keys; one form per [RecordType]. */
enum class Field { TEXT, LANGUAGE, URL, PHONE, BODY, EMAIL, SUBJECT, NAME, ORGANIZATION, ADDRESS, LATITUDE, LONGITUDE, SSID, WIFI_KEY, MAC, PACKAGE, MIME_TYPE, PAYLOAD }

enum class WifiSecurity(val auth: RichRecords.WifiAuth) {
    WPA2(RichRecords.WifiAuth.WPA2_PERSONAL),
    WPA_WPA2(RichRecords.WifiAuth.WPA_WPA2_PERSONAL),
    OPEN(RichRecords.WifiAuth.OPEN),
}

data class FieldSpec(val field: Field, val required: Boolean, val multiline: Boolean = false, val secret: Boolean = false)

object RecordBuilders {

    fun fields(type: RecordType): List<FieldSpec> = when (type) {
        RecordType.TEXT -> listOf(FieldSpec(Field.TEXT, true, multiline = true), FieldSpec(Field.LANGUAGE, true))
        RecordType.URL -> listOf(FieldSpec(Field.URL, true))
        RecordType.PHONE -> listOf(FieldSpec(Field.PHONE, true))
        RecordType.SMS -> listOf(FieldSpec(Field.PHONE, true), FieldSpec(Field.BODY, false, multiline = true))
        RecordType.EMAIL -> listOf(FieldSpec(Field.EMAIL, true), FieldSpec(Field.SUBJECT, false), FieldSpec(Field.BODY, false, multiline = true))
        RecordType.CONTACT -> listOf(
            FieldSpec(Field.NAME, true), FieldSpec(Field.PHONE, false), FieldSpec(Field.EMAIL, false),
            FieldSpec(Field.ORGANIZATION, false), FieldSpec(Field.ADDRESS, false), FieldSpec(Field.URL, false),
        )
        RecordType.LOCATION -> listOf(FieldSpec(Field.LATITUDE, true), FieldSpec(Field.LONGITUDE, true))
        RecordType.ADDRESS -> listOf(FieldSpec(Field.ADDRESS, true, multiline = true))
        RecordType.WIFI -> listOf(FieldSpec(Field.SSID, true), FieldSpec(Field.WIFI_KEY, false, secret = true))
        RecordType.BLUETOOTH -> listOf(FieldSpec(Field.MAC, true), FieldSpec(Field.NAME, false))
        RecordType.APP -> listOf(FieldSpec(Field.PACKAGE, true))
        RecordType.MIME -> listOf(FieldSpec(Field.MIME_TYPE, true), FieldSpec(Field.PAYLOAD, true, multiline = true))
    }

    fun defaults(type: RecordType): Map<Field, String> = when (type) {
        RecordType.TEXT -> mapOf(Field.LANGUAGE to "en")
        RecordType.URL -> mapOf(Field.URL to "https://")
        RecordType.MIME -> mapOf(Field.MIME_TYPE to "text/plain")
        else -> emptyMap()
    }

    /** @return the record, or null when any field is invalid (the form shows which type failed). */
    fun build(type: RecordType, v: Map<Field, String>, security: WifiSecurity = WifiSecurity.WPA2): RecordSpec? {
        fun f(field: Field) = v[field].orEmpty()
        val spec = when (type) {
            RecordType.TEXT -> RecordSpec.Text(f(Field.TEXT), f(Field.LANGUAGE).trim().ifEmpty { "en" })
            RecordType.URL -> RecordSpec.Uri(f(Field.URL).trim())
            RecordType.PHONE -> RichRecords.phone(f(Field.PHONE))
            RecordType.SMS -> RichRecords.sms(f(Field.PHONE), f(Field.BODY))
            RecordType.EMAIL -> RichRecords.email(f(Field.EMAIL), f(Field.SUBJECT), f(Field.BODY))
            RecordType.CONTACT -> RichRecords.contact(
                RichRecords.Contact(f(Field.NAME), f(Field.PHONE).trim(), f(Field.EMAIL).trim(), f(Field.ORGANIZATION), f(Field.ADDRESS), f(Field.URL).trim()),
            )
            RecordType.LOCATION -> {
                val lat = f(Field.LATITUDE).trim().replace(',', '.').toDoubleOrNull()
                val lon = f(Field.LONGITUDE).trim().replace(',', '.').toDoubleOrNull()
                if (lat == null || lon == null) null else RichRecords.location(lat, lon)
            }
            RecordType.ADDRESS -> RichRecords.address(f(Field.ADDRESS))
            RecordType.WIFI -> RichRecords.wifi(RichRecords.Wifi(f(Field.SSID), security.auth, if (security == WifiSecurity.OPEN) "" else f(Field.WIFI_KEY)))
            RecordType.BLUETOOTH -> RichRecords.bluetooth(f(Field.MAC), f(Field.NAME).trim())
            RecordType.APP -> RichRecords.appLaunch(f(Field.PACKAGE))
            RecordType.MIME -> RecordSpec.Mime(f(Field.MIME_TYPE).trim().lowercase(), f(Field.PAYLOAD))
        } ?: return null
        return spec.takeIf { RecordValidator.validate(it) == null }
    }
}
