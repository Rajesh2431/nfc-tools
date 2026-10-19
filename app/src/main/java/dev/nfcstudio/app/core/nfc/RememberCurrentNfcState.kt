package dev.nfcstudio.app.core.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Observes the real NFC adapter state while the host is RESUMED and refreshes
 * it on every resume, so returning from system settings updates the UI.
 * No cached or simulated values are produced.
 */
@Composable
fun rememberCurrentNfcState(): State<NfcState> {
    val state = remember { mutableStateOf(NfcState.NOT_AVAILABLE) }
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(appContext, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = NfcAvailability.current(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        observer.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_RESUME)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}
