package com.tapandgo.core.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

private val READER_FLAGS: Int =
    NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V

/**
 * Enables platform reader mode for as long as this composable is in the
 * composition; discovered tags go to [onTag] on a binder thread.
 *
 * Reader mode stays on for the whole screen — callers filter tags by state
 * instead of toggling it — because disabling reader mode restarts polling,
 * which can drop a tag mid-write and lets the system dispatch the tag to other
 * apps (e.g. a browser). The platform pauses reader mode with the activity and
 * restores it on resume; we disable it explicitly when leaving the screen.
 */
@Composable
fun NfcReaderEffect(onTag: (Tag) -> Unit) {
    val context = LocalContext.current
    val currentOnTag by rememberUpdatedState(onTag)

    DisposableEffect(context) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        val armed = if (adapter != null && activity != null) {
            runCatching {
                adapter.enableReaderMode(activity, { tag: Tag? -> tag?.let { currentOnTag(it) } }, READER_FLAGS, null)
            }.isSuccess
        } else {
            false
        }
        onDispose {
            if (armed && activity != null) runCatching { adapter?.disableReaderMode(activity) }
        }
    }
}

/** Unwraps ContextWrapper chains (e.g. themes) to reach the hosting Activity. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
