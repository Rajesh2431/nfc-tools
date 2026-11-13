package com.tapandgo.root

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

/** Read-only diagnostics available without root (public APIs and world-readable /proc, /sys). */
object DeviceDiagnostics {
    data class Item(val label: String, val value: String)

    fun collect(context: Context): List<Item> = buildList {
        add(Item("Device", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"))
        add(Item("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT} · patch ${if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "?"}"))
        add(Item("Kernel", System.getProperty("os.version") ?: "unknown"))
        add(Item("ABIs", Build.SUPPORTED_ABIS.joinToString()))
        add(Item("Hardware", if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE))
        cpuModel()?.let { add(Item("CPU", it)) }
        add(Item("CPU cores", Runtime.getRuntime().availableProcessors().toString()))
        cpuFrequencies().takeIf { it.isNotEmpty() }?.let { add(Item("CPU MHz (current)", it.joinToString(" "))) }
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        add(Item("RAM", "${mem.availMem / MB} MB free of ${mem.totalMem / MB} MB" + if (mem.lowMemory) " (low)" else ""))
        runCatching {
            val s = StatFs(Environment.getDataDirectory().path)
            add(Item("Storage", "${s.availableBytes / MB} MB free of ${s.totalBytes / MB} MB"))
        }
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery != null) {
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val volt = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            add(Item("Battery", "${if (level >= 0) level * 100 / scale else "?"}% · " +
                (if (temp != Int.MIN_VALUE) "${temp / 10.0} °C · " else "") + (if (volt > 0) "$volt mV" else "")))
        }
        thermalZones().takeIf { it.isNotEmpty() }?.let { add(Item("Thermal (readable zones)", it.joinToString("\n"))) }
    }

    private const val MB = 1024L * 1024L

    private fun cpuModel(): String? = runCatching {
        File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }?.substringAfter(':')?.trim()
    }.getOrNull()

    private fun cpuFrequencies(): List<String> = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { i ->
        runCatching { (File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLong() / 1000).toString() }.getOrNull()
    }

    private fun thermalZones(): List<String> = (0 until 40).mapNotNull { i ->
        val dir = File("/sys/class/thermal/thermal_zone$i")
        if (!dir.exists()) return@mapNotNull null
        runCatching {
            val t = File(dir, "temp").readText().trim().toDouble()
            "${File(dir, "type").readText().trim()}: ${if (t > 1000) t / 1000 else t} °C"
        }.getOrNull()
    }.take(12)
}
