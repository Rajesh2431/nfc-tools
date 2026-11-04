package com.tapandgo.core.nfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Observes the real NFC adapter state: refreshed on every resume (returning
 * from system settings) and on ACTION_ADAPTER_STATE_CHANGED (quick-settings
 * toggle while the app is visible). No cached or simulated values.
 */
@Composable
fun rememberCurrentNfcState(): State<NfcState> {
    val appContext = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(NfcAvailability.current(appContext)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(appContext, lifecycleOwner) {
        val refresh = { state.value = NfcAvailability.current(appContext) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // The system sends this protected broadcast; NOT_EXPORTED still receives it.
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }
    return state
}
