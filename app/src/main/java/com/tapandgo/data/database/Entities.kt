package com.tapandgo.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One scan in local history (spec § 8 ScanEntry). Payload and UID columns are
 * filled only when the user enabled them in Settings › Privacy.
 */
@Entity(tableName = "scan_entries", indices = [Index("createdAt")])
data class ScanEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** Decoded record summaries joined for display and search (user content, on-device only). */
    val summary: String,
    /** Comma-separated technology names. */
    val technologies: String,
    val recordCount: Int,
    /** [ContentKind] name. */
    val contentKind: String,
    /** "OK" or an error code such as "TAG_LOST". */
    val result: String,
    val ndefType: String?,
    val uidHex: String?,
    val payloadHex: String?,
    val favourite: Boolean = false,
)

/** What a history entry describes; stored by name. */
enum class ContentKind { RECORDS, EMPTY, MALFORMED, UNREADABLE, NO_NDEF, ERROR }

/** Reusable NDEF record set (spec § 8 NdefTemplate). */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    /** JSON list of RecordDto; validated on every load. */
    val recordsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Task profile (spec § 8 TaskProfile). Actions are allowlisted JSON data, re-validated on load. */
@Entity(tableName = "task_profiles")
data class TaskProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val actionsJson: String,
    /** ConfirmationPolicy name. */
    val policy: String,
    val triggerJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Added in schema v3 (opt-in auto-run, ADR 0001). */
    @ColumnInfo(defaultValue = "0") val autoRun: Boolean = false,
)

/** Local execution log. Stores action type and outcome only — never parameters. */
@Entity(tableName = "task_log", indices = [Index("createdAt")])
data class TaskLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val profileName: String,
    val actionIndex: Int,
    val actionType: String,
    val outcome: String,
    val reason: String?,
    val createdAt: Long,
)

/**
 * A tag the user explicitly saved to the library (schema v4). Unlike history,
 * the NDEF message is always kept because the user chose to save it; it is
 * what "Restore to tag" writes back.
 */
@Entity(tableName = "saved_tags", indices = [Index("folder"), Index("updatedAt")])
data class SavedTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val notes: String = "",
    /** LibraryIcon name. */
    val icon: String = "TAG",
    val folder: String = "",
    /** Comma-separated labels. */
    val labels: String = "",
    val favourite: Boolean = false,
    val uidHex: String? = null,
    val technologies: String = "",
    val chipName: String? = null,
    val ndefType: String? = null,
    val capacityBytes: Int? = null,
    val writable: Boolean? = null,
    /** Encoded NDEF message; null when the tag held none. */
    val payloadHex: String? = null,
    /** File name inside app-private storage (tag-photos/), never an external URI. */
    val photoFile: String? = null,
    val scannedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Automation workflow (schema v5). The block tree is validated JSON; never code. */
@Entity(tableName = "workflows", indices = [Index(value = ["triggerToken"], unique = true)])
data class WorkflowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "BOLT",
    val category: String = "",
    val workflowJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** v6: random token for tag / shortcut / tile / external triggers; never exported. */
    val triggerToken: String? = null,
    /** v6: run without confirmation when a trigger token matches (opt-in, ADR 0002). */
    @androidx.room.ColumnInfo(defaultValue = "0") val autoRun: Boolean = false,
    /** v6: other apps may request a run with the token (also needs the global setting). */
    @androidx.room.ColumnInfo(defaultValue = "0") val allowExternal: Boolean = false,
    /** v6: periodic background run interval; 0 = off, otherwise >= 15 (WorkManager minimum). */
    @androidx.room.ColumnInfo(defaultValue = "0") val scheduleMinutes: Int = 0,
)

/** One execution (schema v5). Details are masked step logs; no parameter values. */
@Entity(
    tableName = "workflow_runs",
    indices = [Index("workflowId"), Index("startTime")],
    foreignKeys = [androidx.room.ForeignKey(entity = WorkflowEntity::class, parentColumns = ["id"], childColumns = ["workflowId"], onDelete = androidx.room.ForeignKey.CASCADE)],
)
data class WorkflowRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowId: Long,
    val workflowName: String,
    val startTime: Long,
    val endTime: Long,
    val status: String,
    val errorMessage: String?,
    val detailsJson: String,
    /** v6: what started the run (RunSource name). */
    @androidx.room.ColumnInfo(defaultValue = "MANUAL") val source: String = "MANUAL",
)

/**
 * Smart-home connection (schema v7): Home Assistant, MQTT broker, Hue bridge
 * or a local HTTP device. [secretEnc] is the token/password encrypted with an
 * Android Keystore key (never plaintext); it is never exported.
 */
@Entity(tableName = "smart_connections", indices = [Index(value = ["name"], unique = true)])
data class SmartConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    /** Host name or IP, or a full base URL for Home Assistant. */
    val address: String,
    val port: Int,
    val useTls: Boolean,
    val username: String = "",
    val secretEnc: String? = null,
    /** Kind-specific options (validated JSON), e.g. MQTT client id. */
    val extraJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
)

/** Wi-Fi / location trigger for a workflow (schema v7). Location values stay on the device. */
@Entity(
    tableName = "context_triggers",
    indices = [Index("workflowId")],
    foreignKeys = [androidx.room.ForeignKey(entity = WorkflowEntity::class, parentColumns = ["id"], childColumns = ["workflowId"], onDelete = androidx.room.ForeignKey.CASCADE)],
)
data class ContextTriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowId: Long,
    /** WIFI_CONNECT, WIFI_DISCONNECT, AREA_ENTER, AREA_EXIT */
    val type: String,
    val ssid: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
    val enabled: Boolean = true,
    val createdAt: Long,
)
