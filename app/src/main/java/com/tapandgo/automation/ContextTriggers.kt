package com.tapandgo.automation

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tapandgo.billing.EntitlementRepository
import com.tapandgo.billing.PremiumFeature
import com.tapandgo.data.database.ContextTriggerDao
import com.tapandgo.data.database.ContextTriggerEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ContextType { WIFI_CONNECT, WIFI_DISCONNECT, AREA_ENTER, AREA_EXIT }

/** Pure rules for context triggers. */
object ContextRules {
    fun normalizeSsid(raw: String?): String? = raw?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }

    fun isValidSsid(s: String) = s.isNotBlank() && s.toByteArray().size <= 32

    enum class Problem { SSID, LOCATION, RADIUS }

    fun validate(type: ContextType, ssid: String?, lat: Double?, lng: Double?, radius: Int?): Problem? = when (type) {
        ContextType.WIFI_CONNECT, ContextType.WIFI_DISCONNECT -> if (ssid == null || !isValidSsid(ssid)) Problem.SSID else null
        ContextType.AREA_ENTER, ContextType.AREA_EXIT -> when {
            lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0 -> Problem.LOCATION
            radius == null || radius !in 50..10_000 -> Problem.RADIUS
            else -> null
        }
    }

    /** Which Wi-Fi triggers fire when the connected SSID changes from [last] to [now]. */
    fun wifiEvents(last: String?, now: String?, triggers: List<ContextTriggerEntity>): List<ContextTriggerEntity> {
        if (last == now) return emptyList()
        return triggers.filter { t ->
            when (t.type) {
                ContextType.WIFI_CONNECT.name -> now != null && t.ssid == now
                ContextType.WIFI_DISCONNECT.name -> last != null && t.ssid == last
                else -> false
            }
        }
    }

    fun areaEvent(t: ContextTriggerEntity, entering: Boolean): Boolean =
        (t.type == ContextType.AREA_ENTER.name && entering) || (t.type == ContextType.AREA_EXIT.name && !entering)
}

/**
 * Registers Wi-Fi and geofence triggers with the platform (no Play Services):
 * a network callback delivered by PendingIntent, and LocationManager
 * proximity alerts. Firing enqueues the workflow as background work
 * (RunSource.CONTEXT): actions that open screens report Unsupported.
 *
 * Limits (shown in the UI): reading the Wi-Fi name needs precise location and
 * "Allow all the time"; a Wi-Fi disconnect is noticed when Android reports the
 * next network change; geofences are approximate and Android may delay them.
 */
@Singleton
class ContextTriggerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ContextTriggerDao,
    private val entitlements: EntitlementRepository,
) {
    private val prefs get() = context.getSharedPreferences("context_triggers", Context.MODE_PRIVATE)

    fun hasFineLocation() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackgroundLocation() = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun currentSsid(): String? {
        if (!hasFineLocation()) return null
        val wm = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return ContextRules.normalizeSsid(wm.connectionInfo?.ssid)
    }

    @SuppressLint("MissingPermission")
    fun lastLocation(): Pair<Double, Double>? {
        if (!hasFineLocation()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return loc?.let { it.latitude to it.longitude }
    }

    private fun receiverIntent(action: String, id: Long = 0) =
        Intent(context, ContextTriggerReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)

    private fun networkPending() = PendingIntent.getBroadcast(
        context, 0, receiverIntent(ACTION_NETWORK),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    private fun areaPending(id: Long, flags: Int = 0) = PendingIntent.getBroadcast(
        context, (1000 + id).toInt(), receiverIntent(ACTION_AREA, id),
        // Mutable: LocationManager adds KEY_PROXIMITY_ENTERING to this explicit intent.
        PendingIntent.FLAG_UPDATE_CURRENT or flags or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    /** Re-registers everything from the database (app start, boot, any edit). */
    @SuppressLint("MissingPermission")
    suspend fun sync() {
        val premium = entitlements.current().allows(PremiumFeature.CONTEXT_TRIGGERS)
        val all = if (premium) dao.enabled() else emptyList()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        runCatching { cm.unregisterNetworkCallback(networkPending()) }
        if (all.any { it.type.startsWith("WIFI") }) {
            val req = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            runCatching { cm.registerNetworkCallback(req, networkPending()) }
            if (prefs.getString(KEY_SSID, null) == null) currentSsid()?.let { prefs.edit().putString(KEY_SSID, it).apply() }
        }
        val lm = context.getSystemService(LocationManager::class.java)
        val previous = prefs.getStringSet(KEY_AREAS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        previous.forEach { id -> runCatching { lm.removeProximityAlert(areaPending(id)) } }
        val areas = all.filter { it.type.startsWith("AREA") && it.latitude != null && it.longitude != null && it.radiusMeters != null }
        val registered = mutableSetOf<String>()
        if (hasFineLocation()) areas.forEach { t ->
            if (runCatching { lm.addProximityAlert(t.latitude!!, t.longitude!!, t.radiusMeters!!.toFloat(), -1, areaPending(t.id)) }.isSuccess) registered += t.id.toString()
        }
        prefs.edit().putStringSet(KEY_AREAS, registered).apply()
    }

    suspend fun onNetworkChanged() {
        val now = currentSsid()
        val last = prefs.getString(KEY_SSID, null)
        prefs.edit().putString(KEY_SSID, now).apply()
        if (!entitlements.current().allows(PremiumFeature.CONTEXT_TRIGGERS)) return
        ContextRules.wifiEvents(last, now, dao.enabled()).forEach { fire(it.workflowId) }
    }

    suspend fun onArea(id: Long, entering: Boolean) {
        val t = dao.byId(id) ?: return
        if (!t.enabled || !entitlements.current().allows(PremiumFeature.CONTEXT_TRIGGERS)) return
        if (ContextRules.areaEvent(t, entering)) fire(t.workflowId)
    }

    private fun fire(workflowId: Long) {
        val req = OneTimeWorkRequestBuilder<WorkflowWorker>()
            .setInputData(workDataOf(WorkflowWorker.KEY_ID to workflowId, WorkflowWorker.KEY_SOURCE to RunSource.CONTEXT.name))
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    companion object {
        const val ACTION_NETWORK = "com.tapandgo.context.NETWORK"
        const val ACTION_AREA = "com.tapandgo.context.AREA"
        const val EXTRA_ID = "trigger_id"
        private const val KEY_SSID = "last_ssid"
        private const val KEY_AREAS = "registered_areas"
    }
}

/** Receives the PendingIntents registered above (not exported). */
class ContextTriggerReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun manager(): ContextTriggerManager }

    override fun onReceive(context: Context, intent: Intent) {
        val manager = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).manager()
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ContextTriggerManager.ACTION_NETWORK -> manager.onNetworkChanged()
                    ContextTriggerManager.ACTION_AREA -> {
                        if (intent.hasExtra(LocationManager.KEY_PROXIMITY_ENTERING)) {
                            manager.onArea(intent.getLongExtra(ContextTriggerManager.EXTRA_ID, 0), intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false))
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object { internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}

/** Geofences and network callbacks do not survive a reboot or app update; re-register them. */
class ContextBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val manager = EntryPointAccessors.fromApplication(context.applicationContext, ContextTriggerReceiver.Deps::class.java).manager()
        val pending = goAsync()
        ContextTriggerReceiver.scope.launch { try { manager.sync() } finally { pending.finish() } }
    }
}
