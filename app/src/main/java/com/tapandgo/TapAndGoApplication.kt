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

    override fun onCreate() {
        super.onCreate()
        scope.launch { history.prune() }
    }
}
