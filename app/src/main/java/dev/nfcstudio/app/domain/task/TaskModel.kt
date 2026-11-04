package dev.nfcstudio.app.domain.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Allowlisted task actions (spec 4.4). Each is plain data with validated
 * parameters; nothing here can express code, shell commands or arbitrary
 * intents. Profiles are app-side rules — a tag cannot run them by itself.
 */
@Serializable
sealed class TaskAction {
    /** True when the action leaves the app or can affect other apps/people. */
    abstract val isExternal: Boolean

    @Serializable
    @SerialName("open_url")
    data class OpenUrl(val url: String) : TaskAction() {
        override val isExternal get() = true
    }

    @Serializable
    @SerialName("open_map")
    data class OpenMap(val query: String) : TaskAction() {
        override val isExternal get() = true
    }

    @Serializable
    @SerialName("display_text")
    data class DisplayText(val text: String) : TaskAction() {
        override val isExternal get() = false
    }

    @Serializable
    @SerialName("draft_email")
    data class DraftEmail(val to: String, val subject: String = "", val body: String = "") : TaskAction() {
        override val isExternal get() = true
    }

    @Serializable
    @SerialName("draft_sms")
    data class DraftSms(val number: String, val body: String = "") : TaskAction() {
        override val isExternal get() = true
    }

    /** Shown inside the app; saving to contacts is a separate explicit tap. */
    @Serializable
    @SerialName("show_contact")
    data class ShowContact(val name: String, val phone: String = "", val email: String = "") : TaskAction() {
        override val isExternal get() = false
    }

    @Serializable
    @SerialName("open_app")
    data class OpenApp(val packageName: String) : TaskAction() {
        override val isExternal get() = true
    }

    /** Opens a system Settings screen; apps cannot toggle these settings directly. */
    @Serializable
    @SerialName("open_settings")
    data class OpenSettings(val panel: SettingsPanel) : TaskAction() {
        override val isExternal get() = true
    }
}

@Serializable
enum class SettingsPanel { NFC, WIFI, BLUETOOTH, SOUND, DISPLAY, LOCATION }

enum class ConfirmationPolicy {
    /** Confirm actions that leave the app (default, minimum). */
    EXTERNAL_ONLY,

    /** Confirm every action, including in-app display. */
    EVERY_ACTION,
}

/** Optional scan match that *suggests* a profile after a scan; it never auto-runs. */
@Serializable
sealed class TaskTrigger {
    @Serializable
    @SerialName("text_contains")
    data class TextContains(val value: String) : TaskTrigger()

    @Serializable
    @SerialName("uri_host")
    data class UriHost(val host: String) : TaskTrigger()
}

data class TaskProfile(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val actions: List<TaskAction>,
    val policy: ConfirmationPolicy = ConfirmationPolicy.EXTERNAL_ONLY,
    val trigger: TaskTrigger? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** External effects always need confirmation; the policy can only add more. */
fun TaskAction.requiresConfirmation(policy: ConfirmationPolicy): Boolean =
    isExternal || policy == ConfirmationPolicy.EVERY_ACTION
