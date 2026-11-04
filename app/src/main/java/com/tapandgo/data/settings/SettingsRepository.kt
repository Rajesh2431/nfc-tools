package com.tapandgo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Non-sensitive preferences (spec § 8 AppSettings). Privacy-protective defaults. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Save scans to history at all (summary + metadata). */
    val historyEnabled: Boolean = true,
    /** Also store the full NDEF message bytes. Off by default (spec 4.5). */
    val savePayload: Boolean = false,
    /** Also store the tag UID. Off by default; UIDs can identify people. */
    val storeUid: Boolean = false,
    /** 0 = keep forever. Favourites are never pruned. */
    val retentionDays: Int = 0,
    /** Shows raw protocol tools (APDU console). */
    val advancedMode: Boolean = false,
    /** Lets the APDU console send state-changing commands. */
    val allowStateChangingApdu: Boolean = false,
) {
    companion object {
        val RETENTION_CHOICES = listOf(0, 30, 90, 365)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.dataStore)

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val PAYLOAD = booleanPreferencesKey("save_payload")
        val UID = booleanPreferencesKey("store_uid")
        val RETENTION = intPreferencesKey("retention_days")
        val ADVANCED = booleanPreferencesKey("advanced_mode")
        val APDU_WRITE = booleanPreferencesKey("allow_state_changing_apdu")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            dynamicColor = p[Keys.DYNAMIC] ?: defaults.dynamicColor,
            historyEnabled = p[Keys.HISTORY] ?: defaults.historyEnabled,
            savePayload = p[Keys.PAYLOAD] ?: defaults.savePayload,
            storeUid = p[Keys.UID] ?: defaults.storeUid,
            retentionDays = p[Keys.RETENTION]?.takeIf { it in AppSettings.RETENTION_CHOICES } ?: defaults.retentionDays,
            advancedMode = p[Keys.ADVANCED] ?: defaults.advancedMode,
            allowStateChangingApdu = p[Keys.APDU_WRITE] ?: defaults.allowStateChangingApdu,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val next = transform(readFrom(p))
            p[Keys.THEME] = next.theme.name
            p[Keys.DYNAMIC] = next.dynamicColor
            p[Keys.HISTORY] = next.historyEnabled
            p[Keys.PAYLOAD] = next.savePayload
            p[Keys.UID] = next.storeUid
            p[Keys.RETENTION] = next.retentionDays
            p[Keys.ADVANCED] = next.advancedMode
            // State-changing APDUs are only meaningful inside advanced mode.
            p[Keys.APDU_WRITE] = next.allowStateChangingApdu && next.advancedMode
        }
    }

    private fun readFrom(p: Preferences): AppSettings = AppSettings(
        theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = p[Keys.DYNAMIC] ?: true,
        historyEnabled = p[Keys.HISTORY] ?: true,
        savePayload = p[Keys.PAYLOAD] ?: false,
        storeUid = p[Keys.UID] ?: false,
        retentionDays = p[Keys.RETENTION] ?: 0,
        advancedMode = p[Keys.ADVANCED] ?: false,
        allowStateChangingApdu = p[Keys.APDU_WRITE] ?: false,
    )
}
