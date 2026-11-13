package com.tapandgo.automation

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tapandgo.MainActivity
import com.tapandgo.R
import com.tapandgo.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that runs the workflow chosen in its Triggers screen.
 * The tap opens the app's run screen (through the non-exported alias) so the
 * run is visible and screen-opening actions are allowed.
 */
class WorkflowTileService : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun workflows(): WorkflowRepository
        fun settings(): SettingsRepository
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val deps by lazy { EntryPointAccessors.fromApplication(applicationContext, Deps::class.java) }

    private suspend fun chosen(): Pair<Long, String>? {
        val id = deps.settings().current().tileWorkflowId.takeIf { it != 0L } ?: return null
        val (e, wf) = deps.workflows().load(id) ?: return null
        return if (wf == null) null else id to e.name
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val tile = qsTile ?: return@launch
            val c = chosen()
            tile.label = c?.second ?: getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (c == null) getString(R.string.tile_choose) else getString(R.string.wf_title)
            tile.state = if (c == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val c = chosen()
            val intent = if (c == null) {
                Intent(this@WorkflowTileService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                val token = deps.workflows().ensureToken(c.first) ?: return@launch
                LaunchIntents.internal(this@WorkflowTileService, token, RunSource.TILE)
            }
            val open = { launchFromTile(intent) }
            if (isLocked) unlockAndRun(open) else open()
        }
    }

    @Suppress("DEPRECATION")
    private fun launchFromTile(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
