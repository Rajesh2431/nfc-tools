package com.tapandgo.automation

import com.tapandgo.core.ndef.RichRecords

/** A request to run the workflow (or task profile) that owns [token]. */
data class LaunchRequest(val token: String, val source: RunSource)

/**
 * Entry points that carry a trigger token into the app. Shortcuts and the
 * Quick Settings tile target the non-exported [INTERNAL_ALIAS], so only this
 * app (and the launcher acting for it) can use them. Other apps may only use
 * the exported [EXTERNAL_ALIAS], which is treated as untrusted: it needs the
 * global setting, the per-workflow opt-in and the secret token.
 */
object WorkflowLaunch {
    const val ACTION_RUN = "com.tapandgo.action.RUN_WORKFLOW"
    const val EXTRA_TOKEN = "com.tapandgo.extra.WORKFLOW_TOKEN"
    const val EXTRA_SOURCE = "com.tapandgo.extra.SOURCE"
    const val INTERNAL_ALIAS = "com.tapandgo.InternalLaunch"
    const val EXTERNAL_ALIAS = "com.tapandgo.ExternalLaunch"

    /**
     * Pure parser for a run intent. [componentClass] is the alias the system
     * resolved, which a caller cannot forge: other apps can only reach exported
     * components.
     */
    fun parse(action: String?, componentClass: String?, token: String?, source: String?): LaunchRequest? {
        if (action != ACTION_RUN || token == null || !RichRecords.isValidToken(token)) return null
        return when (componentClass) {
            INTERNAL_ALIAS -> {
                val s = source?.let { runCatching { RunSource.valueOf(it) }.getOrNull() }
                if (s == RunSource.SHORTCUT || s == RunSource.TILE) LaunchRequest(token, s) else null
            }
            EXTERNAL_ALIAS -> LaunchRequest(token, RunSource.EXTERNAL)
            else -> null
        }
    }
}

/** Whether a triggered run starts immediately, asks first, or is refused. */
enum class LaunchDecision { RUN, CONFIRM, BLOCKED }

object LaunchPolicy {
    fun decide(source: RunSource, triggers: WorkflowTriggers, externalEnabled: Boolean): LaunchDecision = when (source) {
        // The user tapped Run, a pinned shortcut or the tile: that tap is the confirmation.
        RunSource.MANUAL, RunSource.SHORTCUT, RunSource.TILE, RunSource.SCHEDULE -> LaunchDecision.RUN
        RunSource.NFC_TAG -> if (triggers.autoRun) LaunchDecision.RUN else LaunchDecision.CONFIRM
        RunSource.EXTERNAL -> when {
            !externalEnabled || !triggers.allowExternal -> LaunchDecision.BLOCKED
            triggers.autoRun -> LaunchDecision.RUN
            else -> LaunchDecision.CONFIRM
        }
    }
}
