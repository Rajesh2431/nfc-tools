package com.tapandgo.core.nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The last successfully scanned tag, in memory only (never persisted). */
class CurrentTagStore {
    data class Current(val tag: DiscoveredTag, val snapshot: TagSnapshot)

    private val _current = MutableStateFlow<Current?>(null)
    val current: StateFlow<Current?> = _current.asStateFlow()

    fun set(value: Current?) {
        _current.value = value
    }
}
