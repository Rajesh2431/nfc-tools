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

    private val PHONE = Regex("^\\+?[0-9*#][0-9*# ()-]{0,30}$")
    private val EMAIL = Regex("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}$")

    fun phone(v: String?): String? = v?.trim()?.takeIf { PHONE.matches(it) }?.filterNot { it == ' ' || it == '(' || it == ')' || it == '-' }
    fun isEmail(v: String) = v.length <= 254 && EMAIL.matches(v)

    fun latLng(lat: String?, lng: String?): Pair<Double, Double>? {
        val a = lat?.trim()?.toDoubleOrNull() ?: return null
        val b = lng?.trim()?.toDoubleOrNull() ?: return null
        return if (a in -90.0..90.0 && b in -180.0..180.0) a to b else null
    }

    data class Webhook(val url: String, val method: String, val body: String, val contentType: String)

    /** HTTPS only; GET or POST; body <= 16 KB; content type json or text. */
    fun webhook(url: String?, method: String?, body: String?, contentType: String?): Webhook? {
        val u = url?.trim().orEmpty()
        val parsed = runCatching { java.net.URI(u) }.getOrNull() ?: return null
        if (parsed.scheme?.lowercase() != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null || u.length > 2000) return null
        val m = (method?.trim()?.uppercase()).orEmpty().ifEmpty { "POST" }
        if (m != "GET" && m != "POST") return null
        val b = body.orEmpty()
        if (b.toByteArray().size > 16 * 1024) return null
        val ct = when (contentType?.trim()?.lowercase()) {
            null, "", "json", "application/json" -> "application/json; charset=utf-8"
            "text", "text/plain" -> "text/plain; charset=utf-8"
            else -> return null
        }
        return Webhook(u, m, b, ct)
    }
}
