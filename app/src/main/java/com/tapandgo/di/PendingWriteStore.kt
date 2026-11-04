package com.tapandgo.di

import com.tapandgo.core.ndef.RecordSpec
import kotlinx.coroutines.flow.MutableStateFlow

/** Records another screen hands to the Write tab (e.g. a task tag); consumed once. */
class PendingWriteStore {
    val records = MutableStateFlow<List<RecordSpec>?>(null)
}
