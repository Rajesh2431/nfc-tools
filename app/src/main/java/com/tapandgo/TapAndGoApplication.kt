package com.tapandgo

import android.app.Application
import com.tapandgo.data.repository.HistoryRepository
import com.tapandgo.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Application entry point; Hilt root. Applies history retention once per process. */
@HiltAndroidApp
class TapAndGoApplication : Application() {
    @Inject lateinit var history: HistoryRepository
    @Inject @AppScope lateinit var scope: CoroutineScope
    @Inject lateinit var contextTriggers: com.tapandgo.automation.ContextTriggerManager

    override fun onCreate() {
        super.onCreate()
        scope.launch { history.prune() }
        // Wi-Fi/location registrations live in the platform and are lost on process
        // death of some OEM builds; re-register idempotently.
        scope.launch { runCatching { contextTriggers.sync() } }
    }
}
