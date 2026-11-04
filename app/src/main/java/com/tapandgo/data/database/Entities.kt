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
