package com.tapandgo.automation.actions

/** Pure parameter parsing shared by Android action handlers (JVM-tested). */
object ActionParams {
    fun percent(v: String?): Int? = v?.trim()?.removeSuffix("%")?.toIntOrNull()?.takeIf { it in 0..100 }

    fun onOff(v: String?): Boolean? = when (v?.trim()?.lowercase()) {
        "on", "true", "1", "enable", "enabled" -> true
        "off", "false", "0", "disable", "disabled" -> false
        else -> null
    }

    /** "toggle" or on/off; null = invalid. */
    fun switchTarget(v: String?, current: Boolean): Boolean? =
        if (v?.trim()?.lowercase() == "toggle") !current else onOff(v)

    fun hourMinute(v: String?): Pair<Int, Int>? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(v?.trim().orEmpty()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    fun seconds(v: String?, max: Int): Int? = v?.trim()?.toIntOrNull()?.takeIf { it in 1..max }

    /** Scales a percentage onto a 0..max integer range (rounding half up). */
    fun scale(percent: Int, max: Int): Int = ((percent * max) + 50) / 100

    enum class Stream { MEDIA, RING, NOTIFICATION, ALARM, SYSTEM }

    fun stream(v: String?): Stream? = Stream.entries.firstOrNull { it.name.equals(v?.trim(), ignoreCase = true) }

    enum class Ringer { NORMAL, VIBRATE, SILENT }

    fun ringer(v: String?): Ringer? = Ringer.entries.firstOrNull { it.name.equals(v?.trim(), ignoreCase = true) }
}
