package com.tapandgo.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tapandgo.BuildConfig
import com.tapandgo.R
import com.tapandgo.data.settings.AppSettings
import com.tapandgo.data.settings.ThemeMode
import com.tapandgo.feature.writer.Confirm
import com.tapandgo.ui.components.BackupMessage

/** Theme, privacy, advanced tools, backup and about (spec feature/settings). */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = hiltViewModel()
    val settings by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmApdu by rememberSaveable { mutableStateOf(false) }

    var exportPassword by rememberSaveable { mutableStateOf<String?>(null) }
    var askExportPassword by rememberSaveable { mutableStateOf(false) }
    var importPassword by rememberSaveable { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportAll(it, exportPassword?.toCharArray()) }
        exportPassword = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::import)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        message?.let { BackupMessage(it, vm::dismissMessage) }
        if (message == com.tapandgo.data.export.BackupService.Outcome.NeedsPassword || message == com.tapandgo.data.export.BackupService.Outcome.WrongPassword) {
            androidx.compose.material3.OutlinedTextField(
                value = importPassword, onValueChange = { importPassword = it }, singleLine = true,
                label = { Text(stringResource(R.string.backup_password)) },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.Button(onClick = { vm.importWithPassword(importPassword.toCharArray()); importPassword = "" }, enabled = importPassword.isNotEmpty()) {
                Text(stringResource(R.string.settings_import))
            }
        }
        val s = settings ?: return@Column

        Section(stringResource(R.string.settings_appearance)) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = s.theme == mode, role = Role.RadioButton) { vm.update { it.copy(theme = mode) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.theme == mode, onClick = null)
                    Text(
                        stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Toggle(stringResource(R.string.settings_dynamic_color), null, s.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
        }

        Section(stringResource(R.string.settings_privacy)) {
            Toggle(stringResource(R.string.settings_history_enabled), stringResource(R.string.settings_history_enabled_detail), s.historyEnabled) { v ->
                vm.update { it.copy(historyEnabled = v) }
            }
            Toggle(stringResource(R.string.settings_save_payload), stringResource(R.string.settings_save_payload_detail), s.savePayload) { v ->
                vm.update { it.copy(savePayload = v) }
            }
            Toggle(stringResource(R.string.settings_store_uid), stringResource(R.string.settings_store_uid_detail), s.storeUid) { v ->
                vm.update { it.copy(storeUid = v) }
            }
            Text(stringResource(R.string.settings_retention), style = MaterialTheme.typography.titleSmall)
            AppSettings.RETENTION_CHOICES.forEach { days ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = s.retentionDays == days, role = Role.RadioButton) { vm.update { it.copy(retentionDays = days) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.retentionDays == days, onClick = null)
                    Text(
                        if (days == 0) stringResource(R.string.retention_forever) else pluralStringResource(R.plurals.retention_days, days, days),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Muted(stringResource(R.string.settings_retention_detail))
        }

        Section(stringResource(R.string.settings_advanced)) {
            Toggle(stringResource(R.string.settings_advanced_mode), stringResource(R.string.settings_advanced_mode_detail), s.advancedMode) { v ->
                vm.update { it.copy(advancedMode = v) }
            }
            if (s.advancedMode) {
                Toggle(
                    stringResource(R.string.settings_apdu_write),
                    stringResource(R.string.settings_apdu_write_detail),
                    s.allowStateChangingApdu,
                ) { v -> if (v) confirmApdu = true else vm.update { it.copy(allowStateChangingApdu = false) } }
            }
        }

        Section(stringResource(R.string.settings_data)) {
            Muted(stringResource(R.string.settings_data_detail))
            OutlinedButton(onClick = { exportPassword = null; exportLauncher.launch("tapandgo-backup.json") }) { Text(stringResource(R.string.settings_export)) }
            OutlinedButton(onClick = { askExportPassword = true }) { Text(stringResource(R.string.settings_export_encrypted)) }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                Text(stringResource(R.string.settings_import))
            }
            OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear)) }
        }

        Section(stringResource(R.string.settings_about)) {
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            Muted(stringResource(R.string.settings_about_detail))
        }
    }

    if (askExportPassword) {
        var p1 by rememberSaveable { mutableStateOf("") }
        var p2 by rememberSaveable { mutableStateOf("") }
        val valid = p1.length >= com.tapandgo.core.security.BackupCrypto.MIN_PASSWORD && p1 == p2
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askExportPassword = false },
            title = { Text(stringResource(R.string.settings_export_encrypted)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_encrypt_explain, com.tapandgo.core.security.BackupCrypto.MIN_PASSWORD))
                    androidx.compose.material3.OutlinedTextField(p1, { p1 = it }, singleLine = true, label = { Text(stringResource(R.string.backup_password)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    androidx.compose.material3.OutlinedTextField(p2, { p2 = it }, singleLine = true, label = { Text(stringResource(R.string.password_repeat)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(enabled = valid, onClick = {
                    exportPassword = p1; askExportPassword = false
                    exportLauncher.launch("tapandgo-backup-encrypted.json")
                }) { Text(stringResource(R.string.settings_export)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askExportPassword = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (confirmClear) {
        Confirm(
            title = stringResource(R.string.history_clear_title),
            body = stringResource(R.string.history_clear_detail),
            confirm = stringResource(R.string.history_clear),
            destructive = true,
            onConfirm = { confirmClear = false; vm.clearHistory() },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmApdu) {
        Confirm(
            title = stringResource(R.string.settings_apdu_write_title),
            body = stringResource(R.string.settings_apdu_write_confirm),
            confirm = stringResource(R.string.settings_apdu_write_enable),
            destructive = true,
            onConfirm = { confirmApdu = false; vm.update { it.copy(allowStateChangingApdu = true) } },
            onDismiss = { confirmApdu = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Toggle(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Muted(it) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
