package dev.nfcstudio.app.domain.task

import dev.nfcstudio.app.core.ndef.DecodedRecord
import dev.nfcstudio.app.core.ndef.UriPolicy

enum class ActionProblem {
    URL_INVALID, TEXT_EMPTY, TEXT_TOO_LONG, EMAIL_INVALID, PHONE_INVALID, NAME_EMPTY, PACKAGE_INVALID,
}

enum class ProfileProblem { NAME_EMPTY, NAME_TOO_LONG, NO_ACTIONS, TOO_MANY_ACTIONS, INVALID_ACTION, TRIGGER_INVALID }

/** Validation for every action parameter (spec 4.4, § 11 "task action parameter validation"). */
object TaskValidator {
    const val MAX_ACTIONS = 20
    const val MAX_NAME = 80

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,253}\\.[A-Za-z]{2,63}$")
    private val PHONE = Regex("^\\+?[0-9 ()-]{3,20}$")
    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    private fun text(value: String, max: Int, required: Boolean): ActionProblem? = when {
        required && value.isBlank() -> ActionProblem.TEXT_EMPTY
        value.length > max -> ActionProblem.TEXT_TOO_LONG
        value.any { it.isISOControl() && it != '\n' } -> ActionProblem.TEXT_TOO_LONG
        else -> null
    }

    fun validate(action: TaskAction): ActionProblem? = when (action) {
        is TaskAction.OpenUrl -> {
            val scheme = action.url.substringBefore(':').lowercase()
            if (UriPolicy.validate(action.url) != null || scheme !in setOf("http", "https")) ActionProblem.URL_INVALID else null
        }

        is TaskAction.OpenMap -> text(action.query, 200, required = true)
        is TaskAction.DisplayText -> text(action.text, 2000, required = true)
        is TaskAction.DraftEmail -> when {
            !EMAIL.matches(action.to) -> ActionProblem.EMAIL_INVALID
            else -> text(action.subject, 200, false) ?: text(action.body, 2000, false)
        }

        is TaskAction.DraftSms -> if (!PHONE.matches(action.number)) ActionProblem.PHONE_INVALID else text(action.body, 1000, false)
        is TaskAction.ShowContact -> when {
            action.name.isBlank() -> ActionProblem.NAME_EMPTY
            action.phone.isNotEmpty() && !PHONE.matches(action.phone) -> ActionProblem.PHONE_INVALID
            action.email.isNotEmpty() && !EMAIL.matches(action.email) -> ActionProblem.EMAIL_INVALID
            else -> text(action.name, 100, true)
        }

        is TaskAction.OpenApp -> if (action.packageName.length > 150 || !PACKAGE.matches(action.packageName)) ActionProblem.PACKAGE_INVALID else null
        is TaskAction.OpenSettings -> null
    }

    fun validateTrigger(trigger: TaskTrigger?): Boolean = when (trigger) {
        null -> true
        is TaskTrigger.TextContains -> trigger.value.isNotBlank() && trigger.value.length <= 100
        is TaskTrigger.UriHost -> HOST.matches(trigger.host.lowercase()) && trigger.host.length <= 253
    }

    fun validate(profile: TaskProfile): ProfileProblem? = when {
        profile.name.isBlank() -> ProfileProblem.NAME_EMPTY
        profile.name.trim().length > MAX_NAME -> ProfileProblem.NAME_TOO_LONG
        profile.actions.isEmpty() -> ProfileProblem.NO_ACTIONS
        profile.actions.size > MAX_ACTIONS -> ProfileProblem.TOO_MANY_ACTIONS
        profile.actions.any { validate(it) != null } -> ProfileProblem.INVALID_ACTION
        !validateTrigger(profile.trigger) -> ProfileProblem.TRIGGER_INVALID
        else -> null
    }

    /** Whether [profile] should be *suggested* for a scan with [records]. */
    fun matches(profile: TaskProfile, records: List<DecodedRecord>): Boolean {
        if (!profile.enabled) return false
        return when (val t = profile.trigger) {
            null -> false
            is TaskTrigger.TextContains -> records.any { r ->
                listOfNotNull(r.text, r.uri).any { it.contains(t.value, ignoreCase = true) }
            }

            is TaskTrigger.UriHost -> records.any { r ->
                val host = r.uri?.let(UriPolicy::hostOf) ?: return@any false
                val want = t.host.lowercase()
                host == want || host.endsWith(".$want")
            }
        }
    }
}
