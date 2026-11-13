package com.tapandgo.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tapandgo.R

/** Builds intents for the non-exported internal launch alias and pins home-screen shortcuts. */
object LaunchIntents {
    fun internal(context: Context, token: String, source: RunSource): Intent =
        Intent(WorkflowLaunch.ACTION_RUN)
            .setComponent(ComponentName(context.packageName, WorkflowLaunch.INTERNAL_ALIAS))
            .putExtra(WorkflowLaunch.EXTRA_TOKEN, token)
            .putExtra(WorkflowLaunch.EXTRA_SOURCE, source.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun canPinShortcut(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Asks the launcher to pin a shortcut; the launcher shows its own confirmation. */
    fun requestPin(context: Context, id: Long, name: String, token: String): Boolean {
        val info = ShortcutInfoCompat.Builder(context, "workflow-$id")
            .setShortLabel(name.take(25).ifBlank { context.getString(R.string.wf_title) })
            .setLongLabel(name.take(80).ifBlank { context.getString(R.string.wf_title) })
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(internal(context, token, RunSource.SHORTCUT))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
