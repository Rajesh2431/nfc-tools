package dev.nfcstudio.app.feature.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.nfcstudio.app.R
import dev.nfcstudio.app.domain.task.ActionOutcome
import dev.nfcstudio.app.domain.task.ActionProblem
import dev.nfcstudio.app.domain.task.FailureReason
import dev.nfcstudio.app.domain.task.ProfileProblem
import dev.nfcstudio.app.domain.task.SettingsPanel
import dev.nfcstudio.app.domain.task.TaskAction

fun typeLabel(type: ActionDraft.Type): Int = when (type) {
    ActionDraft.Type.OPEN_URL -> R.string.action_open_url
    ActionDraft.Type.OPEN_MAP -> R.string.action_open_map
    ActionDraft.Type.DISPLAY_TEXT -> R.string.action_display_text
    ActionDraft.Type.DRAFT_EMAIL -> R.string.action_draft_email
    ActionDraft.Type.DRAFT_SMS -> R.string.action_draft_sms
    ActionDraft.Type.SHOW_CONTACT -> R.string.action_show_contact
    ActionDraft.Type.OPEN_APP -> R.string.action_open_app
    ActionDraft.Type.OPEN_SETTINGS -> R.string.action_open_settings
}

fun typeLabelOf(storedType: String): Int? =
    ActionDraft.Type.entries.firstOrNull { it.name.lowercase() == storedType }?.let(::typeLabel)

fun panelLabel(panel: SettingsPanel): Int = when (panel) {
    SettingsPanel.NFC -> R.string.panel_nfc
    SettingsPanel.WIFI -> R.string.panel_wifi
    SettingsPanel.BLUETOOTH -> R.string.panel_bluetooth
    SettingsPanel.SOUND -> R.string.panel_sound
    SettingsPanel.DISPLAY -> R.string.panel_display
    SettingsPanel.LOCATION -> R.string.panel_location
}

/** Human description of what an action will do, shown before confirming it. */
@Composable
fun describe(action: TaskAction): String = when (action) {
    is TaskAction.OpenUrl -> stringResource(R.string.describe_open_url, action.url)
    is TaskAction.OpenMap -> stringResource(R.string.describe_open_map, action.query)
    is TaskAction.DisplayText -> stringResource(R.string.describe_display_text, action.text.take(60))
    is TaskAction.DraftEmail -> stringResource(R.string.describe_draft_email, action.to)
    is TaskAction.DraftSms -> stringResource(R.string.describe_draft_sms, action.number)
    is TaskAction.ShowContact -> stringResource(R.string.describe_show_contact, action.name)
    is TaskAction.OpenApp -> stringResource(R.string.describe_open_app, action.packageName)
    is TaskAction.OpenSettings -> stringResource(R.string.describe_open_settings, stringResource(panelLabel(action.panel)))
}

fun actionProblemText(p: ActionProblem): Int = when (p) {
    ActionProblem.URL_INVALID -> R.string.action_problem_url
    ActionProblem.TEXT_EMPTY -> R.string.action_problem_text_empty
    ActionProblem.TEXT_TOO_LONG -> R.string.action_problem_text_long
    ActionProblem.EMAIL_INVALID -> R.string.action_problem_email
    ActionProblem.PHONE_INVALID -> R.string.action_problem_phone
    ActionProblem.NAME_EMPTY -> R.string.action_problem_name
    ActionProblem.PACKAGE_INVALID -> R.string.action_problem_package
}

fun profileProblemText(p: ProfileProblem): Int = when (p) {
    ProfileProblem.NAME_EMPTY -> R.string.template_name_empty
    ProfileProblem.NAME_TOO_LONG -> R.string.template_name_too_long
    ProfileProblem.NO_ACTIONS -> R.string.profile_problem_no_actions
    ProfileProblem.TOO_MANY_ACTIONS -> R.string.profile_problem_too_many
    ProfileProblem.INVALID_ACTION -> R.string.profile_problem_invalid_action
    ProfileProblem.TRIGGER_INVALID -> R.string.profile_problem_trigger
}

fun outcomeLabel(outcome: String): Int = when (runCatching { ActionOutcome.valueOf(outcome) }.getOrNull()) {
    ActionOutcome.DONE -> R.string.outcome_done
    ActionOutcome.SKIPPED -> R.string.outcome_skipped
    ActionOutcome.FAILED -> R.string.outcome_failed
    ActionOutcome.CANCELLED, null -> R.string.outcome_cancelled
}

fun reasonLabel(reason: FailureReason): Int = when (reason) {
    FailureReason.NO_APP -> R.string.reason_no_app
    FailureReason.APP_NOT_FOUND -> R.string.reason_app_not_found
    FailureReason.BLOCKED -> R.string.reason_blocked
    FailureReason.ERROR -> R.string.reason_error
}
