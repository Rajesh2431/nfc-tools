package com.tapandgo.automation.actions

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.tapandgo.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in ${VARIABLES}, computed on demand from public APIs. Values that need
 * a permission the app does not hold (e.g. WIFI_SSID, location) are not
 * offered here rather than faked.
 */
@Singleton
class BuiltinVariables @Inject constructor(@ApplicationContext private val context: Context) {

    val names = listOf(
        "DATE", "TIME", "TIMESTAMP", "BATTERY_LEVEL", "BATTERY_STATUS", "BATTERY_TEMPERATURE", "DEVICE_MODEL",
        "DEVICE_MANUFACTURER", "ANDROID_VERSION", "APP_VERSION", "WIFI_STATE", "BLUETOOTH_STATE", "NETWORK_TYPE", "STORAGE_FREE_MB",
    )

    fun resolve(name: String, now: Long = System.currentTimeMillis()): String? = when (name) {
        "DATE" -> SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now))
        "TIME" -> SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(now))
        "TIMESTAMP" -> now.toString()
        "DEVICE_MODEL" -> Build.MODEL
        "DEVICE_MANUFACTURER" -> Build.MANUFACTURER
        "ANDROID_VERSION" -> Build.VERSION.RELEASE
        "APP_VERSION" -> BuildConfig.VERSION_NAME
        "BATTERY_LEVEL" -> context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }?.toString()
        "BATTERY_STATUS" -> battery()?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let(::batteryStatus)
        "BATTERY_TEMPERATURE" -> battery()?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { "%.1f".format(Locale.ROOT, it / 10.0) }
        "WIFI_STATE" -> context.applicationContext.getSystemService(WifiManager::class.java)?.let { if (it.isWifiEnabled) "on" else "off" }
        "BLUETOOTH_STATE" -> context.getSystemService(BluetoothManager::class.java)?.adapter?.let { if (it.isEnabled) "on" else "off" } ?: "unavailable"
        "NETWORK_TYPE" -> network()
        "STORAGE_FREE_MB" -> runCatching { StatFs(Environment.getDataDirectory().path).availableBytes / (1024 * 1024) }.getOrNull()?.toString()
        else -> null
    }

    private fun battery(): Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun network(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    companion object {
        fun batteryStatus(code: Int): String = when (code) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
    }
}
