package com.tapandgo.feature.home

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapandgo.BuildConfig
import com.tapandgo.data.database.ScanEntryEntity
import com.tapandgo.data.database.TaskLogEntity
import com.tapandgo.data.repository.HistoryRepository
import com.tapandgo.data.repository.StoredProfile
import com.tapandgo.data.repository.TaskRepository
import com.tapandgo.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Facts about this phone, read from the platform — never assumed. */
data class DeviceFacts(
    val model: String,
    val android: String,
    val sdk: Int,
    val nfcFeature: Boolean,
    val hceFeature: Boolean,
    val appVersion: String,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    history: HistoryRepository,
    tasks: TaskRepository,
    private val settings: SettingsRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val recentScans: StateFlow<List<ScanEntryEntity>?> = history.observeRecent(5).state(null)
    val favourites: StateFlow<List<ScanEntryEntity>> = history.observeFavourites(5).state(emptyList())
    val recentRuns: StateFlow<List<TaskLogEntity>> = tasks.observeLog(5).state(emptyList())
    val profiles: StateFlow<List<StoredProfile>> = tasks.observeProfiles().state(emptyList())
    val layout: StateFlow<DashboardLayout> = settings.dashboard.state(DashboardLayout())

    val device = DeviceFacts(
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        android = Build.VERSION.RELEASE,
        sdk = Build.VERSION.SDK_INT,
        nfcFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC),
        hceFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),
        appVersion = BuildConfig.VERSION_NAME,
    )

    fun editLayout(transform: (DashboardLayout) -> DashboardLayout) {
        viewModelScope.launch { settings.updateDashboard(transform) }
    }
}
