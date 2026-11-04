package dev.nfcstudio.app.feature.writer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.nfcstudio.app.R
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.ui.components.IconBadge

fun RecordType.icon(): ImageVector = when (this) {
    RecordType.TEXT -> Icons.Filled.TextFields
    RecordType.URL -> Icons.Filled.Link
    RecordType.PHONE -> Icons.Filled.Phone
    RecordType.SMS -> Icons.Filled.Sms
    RecordType.EMAIL -> Icons.Filled.Email
    RecordType.CONTACT -> Icons.Filled.ContactPage
    RecordType.LOCATION -> Icons.Filled.LocationOn
    RecordType.ADDRESS -> Icons.Filled.Home
    RecordType.WIFI -> Icons.Filled.Wifi
    RecordType.BLUETOOTH -> Icons.Filled.Bluetooth
    RecordType.APP -> Icons.Filled.Android
    RecordType.MIME -> Icons.Filled.DataObject
}

fun RecordType.title(): Int = when (this) {
    RecordType.TEXT -> R.string.rt_text
    RecordType.URL -> R.string.rt_url
    RecordType.PHONE -> R.string.rt_phone
    RecordType.SMS -> R.string.rt_sms
    RecordType.EMAIL -> R.string.rt_email
    RecordType.CONTACT -> R.string.rt_contact
    RecordType.LOCATION -> R.string.rt_location
    RecordType.ADDRESS -> R.string.rt_address
    RecordType.WIFI -> R.string.rt_wifi
    RecordType.BLUETOOTH -> R.string.rt_bluetooth
    RecordType.APP -> R.string.rt_app
    RecordType.MIME -> R.string.rt_mime
}

fun RecordType.subtitle(): Int = when (this) {
    RecordType.TEXT -> R.string.rt_text_detail
    RecordType.URL -> R.string.rt_url_detail
    RecordType.PHONE -> R.string.rt_phone_detail
    RecordType.SMS -> R.string.rt_sms_detail
    RecordType.EMAIL -> R.string.rt_email_detail
    RecordType.CONTACT -> R.string.rt_contact_detail
    RecordType.LOCATION -> R.string.rt_location_detail
    RecordType.ADDRESS -> R.string.rt_address_detail
    RecordType.WIFI -> R.string.rt_wifi_detail
    RecordType.BLUETOOTH -> R.string.rt_bluetooth_detail
    RecordType.APP -> R.string.rt_app_detail
    RecordType.MIME -> R.string.rt_mime_detail
}

private fun Field.label(): Int = when (this) {
    Field.TEXT -> R.string.f_text
    Field.LANGUAGE -> R.string.writer_language_label
    Field.URL -> R.string.f_url
    Field.PHONE -> R.string.f_phone
    Field.BODY -> R.string.f_body
    Field.EMAIL -> R.string.f_email
    Field.SUBJECT -> R.string.f_subject
    Field.NAME -> R.string.f_name
    Field.ORGANIZATION -> R.string.f_organization
    Field.ADDRESS -> R.string.f_address
    Field.LATITUDE -> R.string.f_latitude
    Field.LONGITUDE -> R.string.f_longitude
    Field.SSID -> R.string.f_ssid
    Field.WIFI_KEY -> R.string.f_wifi_key
    Field.MAC -> R.string.f_mac
    Field.PACKAGE -> R.string.f_package
    Field.MIME_TYPE -> R.string.writer_mime_label
    Field.PAYLOAD -> R.string.writer_mime_payload_label
}

private fun Field.keyboard(): KeyboardType = when (this) {
    Field.URL -> KeyboardType.Uri
    Field.PHONE -> KeyboardType.Phone
    Field.EMAIL -> KeyboardType.Email
    Field.LATITUDE, Field.LONGITUDE -> KeyboardType.Decimal
    Field.WIFI_KEY -> KeyboardType.Password
    else -> KeyboardType.Text
}

/** List of record types with icons (Write tab "Add a record"). */
@Composable
fun RecordTypePicker(onPick: (RecordType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.writer_add_record)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(RecordType.entries) { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(t) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(t.icon())
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(t.title()), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(t.subtitle()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Form for one record type; validates through [RecordBuilders] before adding. */
@Composable
fun RecordForm(type: RecordType, onAdd: (RecordSpec) -> Boolean, onDismiss: () -> Unit) {
    val values = remember(type) { mutableStateMapOf<Field, String>().apply { putAll(RecordBuilders.defaults(type)) } }
    var security by rememberSaveable { mutableStateOf(WifiSecurity.WPA2) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconBadge(type.icon()) },
        title = { Text(stringResource(type.title())) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type == RecordType.WIFI) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WifiSecurity.entries.forEach { s ->
                            FilterChip(
                                selected = security == s,
                                onClick = { security = s; invalid = false },
                                label = {
                                    Text(stringResource(when (s) {
                                        WifiSecurity.WPA2 -> R.string.wifi_wpa2
                                        WifiSecurity.WPA_WPA2 -> R.string.wifi_wpa_wpa2
                                        WifiSecurity.OPEN -> R.string.wifi_open
                                    }))
                                },
                            )
                        }
                    }
                }
                RecordBuilders.fields(type).forEach { spec ->
                    if (type == RecordType.WIFI && spec.field == Field.WIFI_KEY && security == WifiSecurity.OPEN) return@forEach
                    OutlinedTextField(
                        value = values[spec.field].orEmpty(),
                        onValueChange = { values[spec.field] = it; invalid = false },
                        label = { Text(stringResource(spec.field.label()) + if (spec.required) " *" else "") },
                        singleLine = !spec.multiline,
                        minLines = if (spec.multiline) 2 else 1,
                        visualTransformation = if (spec.secret) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = spec.field.keyboard()),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == RecordType.WIFI) Text(stringResource(R.string.wifi_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (type == RecordType.APP) Text(stringResource(R.string.app_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (invalid) Text(stringResource(R.string.form_invalid), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val spec = RecordBuilders.build(type, values, security)
                invalid = spec == null || !onAdd(spec)
                if (!invalid) onDismiss()
            }) { Text(stringResource(R.string.writer_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
