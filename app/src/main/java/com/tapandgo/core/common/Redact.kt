package com.tapandgo.core.common

import android.util.Log

/**
 * Logging that never emits payloads or identifiers (AGENTS rule 13).
 * Callers pass only event names and non-sensitive categories; free-form
 * detail is reduced to its type and length.
 */
object SafeLog {
    private const val TAG = "TapAndGo"

    fun event(name: String, category: String? = null) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, if (category == null) name else "$name [$category]")
        }
    }

    fun error(name: String, t: Throwable) {
        Log.w(TAG, "$name: ${Redact.throwable(t)}")
    }
}

/** Pure redaction helpers, unit tested on the JVM. */
object Redact {
    /** Reduces a throwable to its class name; messages can carry tag IDs. */
    fun throwable(t: Throwable): String = t::class.java.simpleName

    /** Replaces free text with a length marker. */
    fun text(value: String?): String = if (value == null) "null" else "<redacted ${value.length} chars>"

    /** Strips hex identifiers of 8+ digits from platform messages before display. */
    fun platformMessage(message: String?): String? =
        message?.replace(Regex("(?i)\\b(0x)?[0-9a-f]{8,}\\b"), "…")
            ?.replace(Regex("(?i)ID:\\s*[^)\\s]+"), "ID: …")
}
