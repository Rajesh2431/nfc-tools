package com.tapandgo.feature.tasks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.net.toUri
import com.tapandgo.domain.task.ActionPerformer
import com.tapandgo.domain.task.FailureReason
import com.tapandgo.domain.task.SettingsPanel
import com.tapandgo.domain.task.TaskAction
import com.tapandgo.ui.components.openUri

/**
 * Maps allowlisted actions to standard Android intents. Messages are only
 * drafted (ACTION_SENDTO) — never sent — and settings screens are opened, not
 * changed, because apps cannot toggle them directly (spec 4.4).
 */
class IntentActionPerformer(private val context: Context) : ActionPerformer {

    override fun perform(action: TaskAction): FailureReason? {
        val intent = when (action) {
            is TaskAction.OpenUrl -> return if (openUri(context, action.url)) null else FailureReason.NO_APP
            is TaskAction.OpenMap -> Intent(Intent.ACTION_VIEW, ("geo:0,0?q=" + Uri.encode(action.query)).toUri())
            is TaskAction.DraftEmail -> Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(action.to))
                if (action.subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, action.subject)
                if (action.body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, action.body)
            }

            is TaskAction.DraftSms -> Intent(Intent.ACTION_SENDTO, ("smsto:" + Uri.encode(action.number)).toUri()).apply {
                if (action.body.isNotEmpty()) putExtra("sms_body", action.body)
            }

            is TaskAction.OpenApp -> context.packageManager.getLaunchIntentForPackage(action.packageName)
                ?: return FailureReason.APP_NOT_FOUND

            is TaskAction.OpenSettings -> Intent(
                when (action.panel) {
                    SettingsPanel.NFC -> Settings.ACTION_NFC_SETTINGS
                    SettingsPanel.WIFI -> Settings.ACTION_WIFI_SETTINGS
                    SettingsPanel.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
                    SettingsPanel.SOUND -> Settings.ACTION_SOUND_SETTINGS
                    SettingsPanel.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
                    SettingsPanel.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                },
            )

            // In-app actions are rendered by the run screen, never performed here.
            is TaskAction.DisplayText, is TaskAction.ShowContact -> return FailureReason.BLOCKED
        }
        return start(intent)
    }

    /** Opens the system "insert contact" screen pre-filled; the user saves there. */
    fun saveContact(action: TaskAction.ShowContact): FailureReason? = start(
        Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            putExtra(ContactsContract.Intents.Insert.NAME, action.name)
            if (action.phone.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.PHONE, action.phone)
            if (action.email.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.EMAIL, action.email)
        },
    )

    private fun start(intent: Intent): FailureReason? = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        FailureReason.NO_APP
    } catch (_: SecurityException) {
        FailureReason.BLOCKED
    }
}
