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
    val dynamicColor: Boolean = false,
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
    /** Lets other apps request workflow runs (each workflow must also opt in). Off by default. */
    val allowExternalTriggers: Boolean = false,
    /** Workflow started by the Quick Settings tile; 0 = none chosen. */
    val tileWorkflowId: Long = 0,
    /** Workflows may use root radio actions (needs real root). Off by default. */
    val rootWorkflowActions: Boolean = false,
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
        val EXTERNAL = booleanPreferencesKey("allow_external_triggers")
        val TILE = androidx.datastore.preferences.core.longPreferencesKey("tile_workflow_id")
        val ROOT_WF = booleanPreferencesKey("root_workflow_actions")
        val WIDGET = stringPreferencesKey("widget_workflow_ids")
        val DEV_PREMIUM = booleanPreferencesKey("dev_premium_testing_only")
        val DASH_ORDER = stringPreferencesKey("dashboard_order")
        val DASH_HIDDEN = stringPreferencesKey("dashboard_hidden")
        val DASH_PINNED = stringPreferencesKey("dashboard_pinned")
    }

    val dashboard: Flow<com.tapandgo.feature.home.DashboardLayout> = store.data.map { p ->
        com.tapandgo.feature.home.DashboardLayout.decode(p[Keys.DASH_ORDER], p[Keys.DASH_HIDDEN], p[Keys.DASH_PINNED])
    }

    suspend fun updateDashboard(transform: (com.tapandgo.feature.home.DashboardLayout) -> com.tapandgo.feature.home.DashboardLayout) {
        store.edit { p ->
            val next = transform(com.tapandgo.feature.home.DashboardLayout.decode(p[Keys.DASH_ORDER], p[Keys.DASH_HIDDEN], p[Keys.DASH_PINNED]))
            val (o, h, pin) = next.encode()
            p[Keys.DASH_ORDER] = o; p[Keys.DASH_HIDDEN] = h; p[Keys.DASH_PINNED] = pin
        }
    }

    /** Workflows shown on the home-screen widget, in order (max 4). */
    val widgetWorkflows: Flow<List<Long>> = store.data.map { p -> p[Keys.WIDGET].orEmpty().split(',').mapNotNull { it.toLongOrNull() }.take(4) }

    /** Adds or removes [id]; returns false when adding would exceed 4. */
    suspend fun setWidgetWorkflow(id: Long, shown: Boolean): Boolean {
        var ok = true
        store.edit { p ->
            val cur = p[Keys.WIDGET].orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            val next = if (shown) { if (id in cur) cur else if (cur.size >= 4) { ok = false; cur } else cur + id } else cur - id
            p[Keys.WIDGET] = next.joinToString(",")
        }
        return ok
    }

    /** Testing-only Premium switch (debug builds); read through EntitlementRepository only. */
    val devPremium: Flow<Boolean> = store.data.map { it[Keys.DEV_PREMIUM] ?: false }
    suspend fun setDevPremium(on: Boolean) { store.edit { it[Keys.DEV_PREMIUM] = on } }

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
            allowExternalTriggers = p[Keys.EXTERNAL] ?: defaults.allowExternalTriggers,
            tileWorkflowId = p[Keys.TILE] ?: defaults.tileWorkflowId,
            rootWorkflowActions = p[Keys.ROOT_WF] ?: defaults.rootWorkflowActions,
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
            p[Keys.EXTERNAL] = next.allowExternalTriggers
            p[Keys.TILE] = next.tileWorkflowId
            p[Keys.ROOT_WF] = next.rootWorkflowActions
        }
    }

    private fun readFrom(p: Preferences): AppSettings = AppSettings(
        theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = p[Keys.DYNAMIC] ?: false,
        historyEnabled = p[Keys.HISTORY] ?: true,
        savePayload = p[Keys.PAYLOAD] ?: false,
        storeUid = p[Keys.UID] ?: false,
        retentionDays = p[Keys.RETENTION] ?: 0,
        advancedMode = p[Keys.ADVANCED] ?: false,
        allowStateChangingApdu = p[Keys.APDU_WRITE] ?: false,
        allowExternalTriggers = p[Keys.EXTERNAL] ?: false,
        tileWorkflowId = p[Keys.TILE] ?: 0,
        rootWorkflowActions = p[Keys.ROOT_WF] ?: false,
    )
}
