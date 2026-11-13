package com.tapandgo.smarthome

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class HaEntity(val entityId: String, val state: String, val friendlyName: String) {
    val domain: String get() = entityId.substringBefore('.')
    val toggleable: Boolean get() = domain in TOGGLE_DOMAINS
    companion object {
        val TOGGLE_DOMAINS = setOf("light", "switch", "fan", "input_boolean", "automation", "media_player", "climate", "cover", "lock")
    }
}

/** Home Assistant REST API (https://developers.home-assistant.io/docs/api/rest). */
class HomeAssistantClient(private val baseUrl: String, private val token: String, private val http: HttpTransport) {
    private val headers get() = mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json")
    private fun url(path: String) = baseUrl.trimEnd('/') + path

    suspend fun check(): SmartResult<String> = when (val r = http.request(url("/api/"), "GET", headers, null)) {
        is SmartResult.Err -> r
        is SmartResult.Ok -> when (r.value.code) {
            200 -> SmartResult.Ok("connected")
            401 -> SmartResult.Err("token rejected (401)")
            else -> SmartResult.Err("HTTP ${r.value.code}")
        }
    }

    suspend fun states(): SmartResult<List<HaEntity>> = when (val r = http.request(url("/api/states"), "GET", headers, null)) {
        is SmartResult.Err -> r
        is SmartResult.Ok -> if (r.value.code != 200) SmartResult.Err("HTTP ${r.value.code}") else parseStates(r.value.body)
    }

    suspend fun state(entityId: String): SmartResult<HaEntity> {
        if (!isEntityId(entityId)) return SmartResult.Err("invalid entity id")
        return when (val r = http.request(url("/api/states/$entityId"), "GET", headers, null)) {
            is SmartResult.Err -> r
            is SmartResult.Ok -> when (r.value.code) {
                200 -> parseEntity(r.value.body)?.let { SmartResult.Ok(it) } ?: SmartResult.Err("unexpected response")
                404 -> SmartResult.Err("entity not found")
                else -> SmartResult.Err("HTTP ${r.value.code}")
            }
        }
    }

    /** Calls `domain.service`, optionally on [entityId] with extra JSON [data]. */
    suspend fun callService(service: String, entityId: String?, data: String?): SmartResult<Unit> {
        val (domain, name) = parseService(service) ?: return SmartResult.Err("service must look like light.turn_on")
        if (entityId != null && entityId.isNotBlank() && !isEntityId(entityId)) return SmartResult.Err("invalid entity id")
        val extra = if (data.isNullOrBlank()) JsonObject(emptyMap()) else
            runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return SmartResult.Err("data must be a JSON object")
        val body = JsonObject(extra + (if (!entityId.isNullOrBlank()) mapOf("entity_id" to JsonPrimitive(entityId)) else emptyMap()))
        return when (val r = http.request(url("/api/services/$domain/$name"), "POST", headers, body.toString())) {
            is SmartResult.Err -> r
            is SmartResult.Ok -> if (r.value.code in 200..299) SmartResult.Ok(Unit) else SmartResult.Err("HTTP ${r.value.code}")
        }
    }

    suspend fun toggle(e: HaEntity): SmartResult<Unit> = callService(
        when (e.domain) {
            "cover" -> "cover.toggle"
            "lock" -> if (e.state == "locked") "lock.unlock" else "lock.lock"
            else -> "${e.domain}.toggle"
        },
        e.entityId, null,
    )

    companion object {
        private val ENTITY = Regex("^[a-z_]+\\.[a-z0-9_]+$")
        private val SERVICE = Regex("^([a-z_]+)\\.([a-z0-9_]+)$")
        fun isEntityId(s: String) = s.length <= 255 && ENTITY.matches(s)
        fun parseService(s: String): Pair<String, String>? = SERVICE.matchEntire(s.trim())?.let { it.groupValues[1] to it.groupValues[2] }

        fun parseEntity(body: String): HaEntity? = runCatching { entityOf(Json.parseToJsonElement(body).jsonObject) }.getOrNull()

        fun parseStates(body: String): SmartResult<List<HaEntity>> = runCatching {
            (Json.parseToJsonElement(body) as JsonArray).mapNotNull { runCatching { entityOf(it.jsonObject) }.getOrNull() }.sortedBy { it.entityId }
        }.fold({ SmartResult.Ok(it) }, { SmartResult.Err("unexpected response") })

        private fun entityOf(o: JsonObject): HaEntity {
            val id = o["entity_id"]!!.jsonPrimitive.content
            val name = (o["attributes"] as? JsonObject)?.get("friendly_name")?.jsonPrimitive?.contentOrNull ?: id
            return HaEntity(id, o["state"]?.jsonPrimitive?.contentOrNull ?: "unknown", name)
        }

    }
}
