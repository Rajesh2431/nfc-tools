package com.tapandgo.core.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reader-mode flags used app-wide by MainActivity. */
val READER_FLAGS: Int =
    NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V

/**
 * Routes tags from the activity-wide reader mode to the screen on top.
 *
 * MainActivity keeps reader mode on for as long as it is resumed, so while
 * Tap & Go is in the foreground Android never hands a tag to another app or
 * the system viewer. Screens register a handler; the most recently registered
 * one wins. Registration is by owner, so a screen leaving during a transition
 * can only remove its own handler (it never turns reader mode off).
 */
open class TagRouter<T : Any>(private val now: () -> Long = System::currentTimeMillis) {
    private class Entry<T>(val owner: Any, val handler: (T) -> Unit)

    private val handlers = mutableListOf<Entry<T>>()
    private var pending: Pair<T, Long>? = null
    private val _unhandled = MutableStateFlow(0L)
    /** Increments when a tag arrived while no screen was listening (UI opens Read). */
    val unhandled: StateFlow<Long> = _unhandled.asStateFlow()


    @Synchronized
    fun register(owner: Any, handler: (T) -> Unit) {
        handlers.removeAll { it.owner === owner }
        handlers += Entry(owner, handler)
        val p = pending
        pending = null
        if (p != null && now() - p.second <= PENDING_MS) handler(p.first)
    }

    @Synchronized
    fun unregister(owner: Any) {
        handlers.removeAll { it.owner === owner }
    }

    /** @return true when a screen consumed the tag. */
    fun dispatch(tag: T): Boolean {
        val top = synchronized(this) { handlers.lastOrNull() } ?: return false
        top.handler(tag)
        return true
    }

    /** No screen listening: keep the tag briefly and ask the UI to open Read. */
    @Synchronized
    fun park(tag: T) {
        pending = tag to now()
        _unhandled.value = _unhandled.value + 1
    }

    @Synchronized
    fun hasHandler(): Boolean = handlers.isNotEmpty()

    private val pausers = mutableSetOf<Any>()
    private val _paused = MutableStateFlow(false)
    /** True while a screen (HCE emulation) needs reader mode off: reader mode disables card emulation. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    @Synchronized
    fun pauseReader(owner: Any) { pausers += owner; _paused.value = true }

    @Synchronized
    fun resumeReader(owner: Any) { pausers -= owner; _paused.value = pausers.isNotEmpty() }
}

/** Max age of a parked tag handed to the next screen that registers. */
const val PENDING_MS = 5_000L

/** The app-wide router for platform tags. */
object NfcTagRouter : TagRouter<Tag>()

/** Turns app-wide reader mode off while in the composition (used by the Emulation screen). */
@Composable
fun PauseNfcReader() {
    DisposableEffect(Unit) {
        val owner = Any()
        NfcTagRouter.pauseReader(owner)
        onDispose { NfcTagRouter.resumeReader(owner) }
    }
}

/**
 * Receives tags for as long as this composable is in the composition; tags
 * arrive on a binder thread. Reader mode itself is owned by MainActivity.
 */
@Composable
fun NfcReaderEffect(onTag: (Tag) -> Unit) {
    val currentOnTag by rememberUpdatedState(onTag)
    DisposableEffect(Unit) {
        val owner = Any()
        NfcTagRouter.register(owner) { currentOnTag(it) }
        onDispose { NfcTagRouter.unregister(owner) }
    }
}

/** Unwraps ContextWrapper chains (e.g. themes) to reach the hosting Activity. */
tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
