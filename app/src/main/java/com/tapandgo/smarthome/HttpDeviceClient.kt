package com.tapandgo.smarthome

/** Local HTTP device APIs with fixed, documented endpoints. */
enum class HttpDeviceType { SHELLY_GEN1, SHELLY_GEN2, TASMOTA, WLED }

/** Builds requests for an on/off/toggle and optional brightness; pure for tests. */
object HttpDeviceRequests {
    data class Req(val path: String, val method: String, val body: String?)

    fun switch(type: HttpDeviceType, on: Boolean?, channel: Int = 0, brightnessPercent: Int? = null): Req? {
        if (channel !in 0..7) return null
        val bri = brightnessPercent?.coerceIn(0, 100)
        return when (type) {
            HttpDeviceType.SHELLY_GEN1 -> Req("/relay/$channel?turn=${when (on) { true -> "on"; false -> "off"; null -> "toggle" }}", "GET", null)
            HttpDeviceType.SHELLY_GEN2 -> if (on == null) Req("/rpc/Switch.Toggle?id=$channel", "GET", null) else Req("/rpc/Switch.Set?id=$channel&on=$on", "GET", null)
            HttpDeviceType.TASMOTA -> {
                val cmd = if (bri != null) "Dimmer%20$bri" else "Power${channel + 1}%20${when (on) { true -> "On"; false -> "Off"; null -> "Toggle" }}"
                Req("/cm?cmnd=$cmd", "GET", null)
            }
            HttpDeviceType.WLED -> {
                val parts = buildList {
                    add("\"on\":" + when (on) { true -> "true"; false -> "false"; null -> "\"t\"" })
                    bri?.let { add("\"bri\":${it * 255 / 100}") }
                }
                Req("/json/state", "POST", parts.joinToString(",", "{", "}"))
            }
        }
    }
}

class HttpDeviceClient(
    private val host: String,
    private val port: Int,
    private val tls: Boolean,
    private val type: HttpDeviceType,
    private val username: String,
    private val password: String?,
    private val http: HttpTransport,
) {
    suspend fun switch(on: Boolean?, channel: Int = 0, brightnessPercent: Int? = null): SmartResult<Unit> {
        val req = HttpDeviceRequests.switch(type, on, channel, brightnessPercent) ?: return SmartResult.Err("invalid channel")
        val base = "${if (tls) "https" else "http"}://$host" + if ((tls && port == 443) || (!tls && port == 80)) "" else ":$port"
        val auth = if (!password.isNullOrEmpty()) {
            val user = username.ifBlank { if (type == HttpDeviceType.TASMOTA) "admin" else "admin" }
            mapOf("Authorization" to "Basic " + java.util.Base64.getEncoder().encodeToString("$user:$password".toByteArray()))
        } else emptyMap()
        val headers = auth + if (req.body != null) mapOf("Content-Type" to "application/json") else emptyMap()
        return when (val r = http.request(base + req.path, req.method, headers, req.body)) {
            is SmartResult.Err -> r
            is SmartResult.Ok -> when (r.value.code) {
                in 200..299 -> SmartResult.Ok(Unit)
                401 -> SmartResult.Err("device rejected the password (401)")
                else -> SmartResult.Err("HTTP ${r.value.code}")
            }
        }
    }

    suspend fun check(): SmartResult<String> {
        val path = when (type) {
            HttpDeviceType.SHELLY_GEN1 -> "/shelly"
            HttpDeviceType.SHELLY_GEN2 -> "/rpc/Shelly.GetDeviceInfo"
            HttpDeviceType.TASMOTA -> "/cm?cmnd=Status"
            HttpDeviceType.WLED -> "/json/info"
        }
        val base = "${if (tls) "https" else "http"}://$host:$port"
        return when (val r = http.request(base + path, "GET", emptyMap(), null)) {
            is SmartResult.Err -> r
            is SmartResult.Ok -> if (r.value.code in 200..299) SmartResult.Ok("reachable") else SmartResult.Err("HTTP ${r.value.code}")
        }
    }
}
