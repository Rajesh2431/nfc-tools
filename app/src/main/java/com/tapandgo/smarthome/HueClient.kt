package com.tapandgo.smarthome

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class HueLight(val id: String, val name: String, val on: Boolean, val reachable: Boolean)
data class HueScene(val id: String, val name: String, val group: String?)

/** Philips Hue bridge, local CLIP v1 API over the LAN. */
class HueClient(private val bridge: String, private val username: String, private val http: HttpTransport) {
    private fun url(path: String) = "http://$bridge/api" + path

    /** Requires the user to press the bridge's link button first. Returns the new app key. */
    suspend fun pair(): SmartResult<String> = when (val r = http.request(url(""), "POST", JSON, """{"devicetype":"tapandgo#android"}""")) {
        is SmartResult.Err -> r
        is SmartResult.Ok -> parsePair(r.value.body)
    }

    suspend fun lights(): SmartResult<List<HueLight>> = get("/$username/lights") { o ->
        o.entries.map { (id, v) ->
            val l = v.jsonObject
            val st = l["state"]?.jsonObject
            HueLight(id, l["name"]?.jsonPrimitive?.contentOrNull ?: id, st?.get("on")?.jsonPrimitive?.booleanOrNull == true, st?.get("reachable")?.jsonPrimitive?.booleanOrNull != false)
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun scenes(): SmartResult<List<HueScene>> = get("/$username/scenes") { o ->
        o.entries.map { (id, v) -> HueScene(id, v.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: id, v.jsonObject["group"]?.jsonPrimitive?.contentOrNull) }
            .sortedBy { it.name.lowercase() }
    }

    /** [on] null keeps power state; [brightnessPercent] 0..100 or null. */
    suspend fun setLight(id: String, on: Boolean?, brightnessPercent: Int?): SmartResult<Unit> {
        if (!ID.matches(id)) return SmartResult.Err("invalid light id")
        val parts = buildList {
            on?.let { add("\"on\":$it") }
            brightnessPercent?.let { add("\"bri\":${(it.coerceIn(0, 100) * 254 / 100).coerceAtLeast(1)}") }
        }
        if (parts.isEmpty()) return SmartResult.Err("nothing to change")
        return put("/$username/lights/$id/state", parts.joinToString(",", "{", "}"))
    }

    suspend fun recallScene(sceneId: String, group: String = "0"): SmartResult<Unit> {
        if (!ID.matches(sceneId) || !ID.matches(group)) return SmartResult.Err("invalid scene id")
        return put("/$username/groups/$group/action", """{"scene":"$sceneId"}""")
    }

    private suspend fun <T> get(path: String, map: (JsonObject) -> T): SmartResult<T> = when (val r = http.request(url(path), "GET", emptyMap(), null)) {
        is SmartResult.Err -> r
        is SmartResult.Ok -> errorIn(r.value.body)?.let { SmartResult.Err(it) }
            ?: runCatching { map(Json.parseToJsonElement(r.value.body).jsonObject) }.fold({ SmartResult.Ok(it) }, { SmartResult.Err("unexpected response") })
    }

    private suspend fun put(path: String, body: String): SmartResult<Unit> = when (val r = http.request(url(path), "PUT", JSON, body)) {
        is SmartResult.Err -> r
        is SmartResult.Ok -> errorIn(r.value.body)?.let { SmartResult.Err(it) } ?: if (r.value.code in 200..299) SmartResult.Ok(Unit) else SmartResult.Err("HTTP ${r.value.code}")
    }

    companion object {
        private val JSON = mapOf("Content-Type" to "application/json")
        private val ID = Regex("^[A-Za-z0-9-]{1,64}$")

        /** Hue returns `[{"error":{"type":101,"description":"link button not pressed"}}]` on failures. */
        fun errorIn(body: String): String? = runCatching {
            (Json.parseToJsonElement(body) as? JsonArray)?.firstNotNullOfOrNull { it.jsonObject["error"]?.jsonObject?.get("description")?.jsonPrimitive?.contentOrNull }
        }.getOrNull()

        fun parsePair(body: String): SmartResult<String> {
            errorIn(body)?.let { return SmartResult.Err(it) }
            val name = runCatching {
                (Json.parseToJsonElement(body) as JsonArray).first().jsonObject["success"]!!.jsonObject["username"]!!.jsonPrimitive.content
            }.getOrNull()
            return name?.let { SmartResult.Ok(it) } ?: SmartResult.Err("unexpected response")
        }
    }
}
