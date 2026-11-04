package com.tapandgo.feature.advanced

import android.content.Context
import android.net.Uri
import com.tapandgo.BuildConfig
import com.tapandgo.core.nfc.NfcState
import com.tapandgo.core.nfc.TagSnapshot
import com.tapandgo.data.repository.HistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Writes payload-free diagnostics to a user-chosen document. */
class DiagnosticsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val history: HistoryRepository,
) {
    suspend fun export(target: Uri, snapshot: TagSnapshot, nfcState: NfcState): Boolean = withContext(Dispatchers.IO) {
        val d = DiagnosticsBuilder.build(
            snapshot, nfcState, BuildConfig.VERSION_NAME, android.os.Build.MANUFACTURER, android.os.Build.MODEL,
            android.os.Build.VERSION.SDK_INT, history.recentResultCodes(),
        )
        runCatching {
            context.contentResolver.openOutputStream(target, "wt")!!.use { it.write(DiagnosticsBuilder.encode(d).toByteArray()) }
        }.isSuccess
    }
}
