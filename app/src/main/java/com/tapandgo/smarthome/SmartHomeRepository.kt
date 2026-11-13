package com.tapandgo.smarthome

import com.tapandgo.core.security.SecretBox
import com.tapandgo.data.database.SmartConnectionEntity
import com.tapandgo.data.database.SmartHomeDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class SmartKind(val defaultPort: Int, val usesTls: Boolean) {
    HOME_ASSISTANT(8123, false), MQTT(1883, true), HUE(80, false),
    SHELLY_GEN1(80, false), SHELLY_GEN2(80, false), TASMOTA(80, false), WLED(80, false);

    val httpDevice: HttpDeviceType? get() = when (this) {
        SHELLY_GEN1 -> HttpDeviceType.SHELLY_GEN1
        SHELLY_GEN2 -> HttpDeviceType.SHELLY_GEN2
        TASMOTA -> HttpDeviceType.TASMOTA
        WLED -> HttpDeviceType.WLED
        else -> null
    }
}

/** Connection as the UI sees it; the secret itself never leaves the repository. */
data class SmartConnection(
    val id: Long,
    val kind: SmartKind,
    val name: String,
    val address: String,
    val port: Int,
    val useTls: Boolean,
    val username: String,
    val hasSecret: Boolean,
)

/** Form input. [secret] null keeps the stored secret; "" clears it. */
data class ConnectionDraft(
    val id: Long = 0,
    val kind: SmartKind = SmartKind.HOME_ASSISTANT,
    val name: String = "",
    val address: String = "",
    val port: Int = SmartKind.HOME_ASSISTANT.defaultPort,
    val useTls: Boolean = false,
    val username: String = "",
    val secret: String? = null,
)

enum class ConnectionProblem { NAME, ADDRESS, PORT, PLAIN_REMOTE, SECRET_REQUIRED, DUPLICATE }

object ConnectionValidator {
    private val NAME = Regex("^[A-Za-z0-9 _.-]{1,40}$")
    private val HOST = Regex("^[A-Za-z0-9.-]{1,253}$|^\\[?[0-9a-fA-F:]{2,39}]?$")

    fun validate(d: ConnectionDraft, hasStoredSecret: Boolean = false): ConnectionProblem? {
        if (!NAME.matches(d.name.trim())) return ConnectionProblem.NAME
        if (d.port !in 1..65_535) return ConnectionProblem.PORT
        if (d.kind == SmartKind.HOME_ASSISTANT) {
            val url = d.address.trim()
            LanPolicy.check(url)?.let { return if (it.startsWith("plain http")) ConnectionProblem.PLAIN_REMOTE else ConnectionProblem.ADDRESS }
        } else {
            if (!HOST.matches(d.address.trim())) return ConnectionProblem.ADDRESS
            if (!d.useTls && !LanPolicy.isLocalHost(d.address)) return ConnectionProblem.PLAIN_REMOTE
        }
        val secretPresent = if (d.secret == null) hasStoredSecret else d.secret.isNotEmpty()
        // Hue gets its key from pairing after saving.
        if (d.kind == SmartKind.HOME_ASSISTANT && !secretPresent) return ConnectionProblem.SECRET_REQUIRED
        return null
    }
}

@Singleton
class SmartHomeRepository(
    private val dao: SmartHomeDao,
    private val box: SecretBox,
    private val http: HttpTransport,
    private val mqttSockets: MqttSocketFactory,
    private val now: () -> Long,
) {
    @Inject constructor(dao: SmartHomeDao, box: SecretBox) : this(dao, box, UrlConnectionTransport(), DefaultMqttSocketFactory(), System::currentTimeMillis)

    private fun SmartConnectionEntity.toModel() = SmartConnection(
        id, runCatching { SmartKind.valueOf(kind) }.getOrDefault(SmartKind.HOME_ASSISTANT), name, address, port, useTls, username, secretEnc != null,
    )

    fun observe(): Flow<List<SmartConnection>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    suspend fun all(): List<SmartConnection> = dao.all().map { it.toModel() }
    suspend fun byId(id: Long): SmartConnection? = dao.byId(id)?.toModel()

    /** @return the id or the validation problem. */
    suspend fun save(d: ConnectionDraft): Pair<Long?, ConnectionProblem?> {
        val existing = if (d.id != 0L) dao.byId(d.id) else null
        ConnectionValidator.validate(d, existing?.secretEnc != null)?.let { return null to it }
        dao.byName(d.name.trim())?.let { if (it.id != d.id) return null to ConnectionProblem.DUPLICATE }
        val sealed = when {
            d.secret == null -> existing?.secretEnc
            d.secret.isEmpty() -> null
            else -> box.seal(d.secret)
        }
        val t = now()
        val e = SmartConnectionEntity(
            id = d.id, kind = d.kind.name, name = d.name.trim(), address = d.address.trim().trimEnd('/'), port = d.port,
            useTls = d.useTls, username = d.username.trim().take(100), secretEnc = sealed,
            createdAt = existing?.createdAt ?: t, updatedAt = t,
        )
        return if (existing == null) dao.insert(e.copy(id = 0)) to null else { dao.update(e); d.id to null }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Workflows refer to connections by name; the in-app screens pass the numeric id. Name wins. */
    private suspend fun entity(nameOrId: String): SmartConnectionEntity? =
        dao.byName(nameOrId.trim()) ?: nameOrId.trim().toLongOrNull()?.let { dao.byId(it) }

    private fun secretOf(e: SmartConnectionEntity): String? = e.secretEnc?.let(box::open)

    private fun kindOf(e: SmartConnectionEntity) = runCatching { SmartKind.valueOf(e.kind) }.getOrNull()

    fun homeAssistant(e: SmartConnectionEntity): SmartResult<HomeAssistantClient> {
        if (kindOf(e) != SmartKind.HOME_ASSISTANT) return SmartResult.Err("“${e.name}” is not a Home Assistant connection")
        val token = secretOf(e) ?: return SmartResult.Err("token unavailable; edit the connection and enter it again")
        return SmartResult.Ok(HomeAssistantClient(e.address, token, http))
    }

    fun hue(e: SmartConnectionEntity): SmartResult<HueClient> {
        if (kindOf(e) != SmartKind.HUE) return SmartResult.Err("“${e.name}” is not a Hue bridge")
        val key = secretOf(e) ?: return SmartResult.Err("bridge not paired; pair it again")
        return SmartResult.Ok(HueClient(hostPort(e), key, http))
    }

    fun mqtt(e: SmartConnectionEntity): SmartResult<MqttPublisher> {
        if (kindOf(e) != SmartKind.MQTT) return SmartResult.Err("“${e.name}” is not an MQTT broker")
        return SmartResult.Ok(MqttPublisher(e.address, e.port, e.useTls, e.username.ifBlank { null }, secretOf(e), "tapandgo-${e.id}", mqttSockets))
    }

    fun httpDevice(e: SmartConnectionEntity): SmartResult<HttpDeviceClient> {
        val type = kindOf(e)?.httpDevice ?: return SmartResult.Err("“${e.name}” is not an HTTP device")
        return SmartResult.Ok(HttpDeviceClient(e.address, e.port, e.useTls, type, e.username, secretOf(e), http))
    }

    private fun hostPort(e: SmartConnectionEntity) = if (e.port == 80) e.address else "${e.address}:${e.port}"

    // ---- Operations used by the UI, workflows and Device Controls ----

    suspend fun test(id: Long): SmartResult<String> {
        val e = dao.byId(id) ?: return SmartResult.Err("connection not found")
        return when (kindOf(e)) {
            SmartKind.HOME_ASSISTANT -> homeAssistant(e).then { it.check() }
            SmartKind.HUE -> hue(e).then { c -> c.lights().map { "${it.size} lights" } }
            SmartKind.MQTT -> mqtt(e).then { c -> c.publish("tapandgo/test", "ping", 0, false).map { "published tapandgo/test" } }
            null -> SmartResult.Err("unknown connection type")
            else -> httpDevice(e).then { it.check() }
        }
    }

    suspend fun haStates(id: Long) = dao.byId(id)?.let { e -> homeAssistant(e).then { it.states() } } ?: SmartResult.Err("connection not found")
    suspend fun haToggle(id: Long, entity: HaEntity) = dao.byId(id)?.let { e -> homeAssistant(e).then { it.toggle(entity) } } ?: SmartResult.Err("connection not found")
    suspend fun hueLights(id: Long) = dao.byId(id)?.let { e -> hue(e).then { it.lights() } } ?: SmartResult.Err("connection not found")
    suspend fun hueScenes(id: Long) = dao.byId(id)?.let { e -> hue(e).then { it.scenes() } } ?: SmartResult.Err("connection not found")
    suspend fun hueSet(id: Long, light: String, on: Boolean?, bri: Int?) = dao.byId(id)?.let { e -> hue(e).then { it.setLight(light, on, bri) } } ?: SmartResult.Err("connection not found")
    suspend fun deviceSwitch(id: Long, on: Boolean?, channel: Int = 0, bri: Int? = null) = dao.byId(id)?.let { e -> httpDevice(e).then { it.switch(on, channel, bri) } } ?: SmartResult.Err("connection not found")

    /** Pairs with a Hue bridge (link button pressed) and stores the app key encrypted. */
    suspend fun pairHue(id: Long): SmartResult<Unit> {
        val e = dao.byId(id) ?: return SmartResult.Err("connection not found")
        if (kindOf(e) != SmartKind.HUE) return SmartResult.Err("not a Hue bridge")
        return when (val r = HueClient(hostPort(e), "", http).pair()) {
            is SmartResult.Err -> r
            is SmartResult.Ok -> { dao.update(e.copy(secretEnc = box.seal(r.value), updatedAt = now())); SmartResult.Ok(Unit) }
        }
    }

    // ---- By name, for workflow actions ----

    suspend fun haCall(conn: String, service: String, entity: String?, data: String?): SmartResult<Unit> =
        entity(conn)?.let { e -> homeAssistant(e).then { it.callService(service, entity, data) } } ?: SmartResult.Err("no connection named “$conn”")

    suspend fun haState(conn: String, entity: String): SmartResult<String> =
        entity(conn)?.let { e -> homeAssistant(e).then { c -> c.state(entity).map { it.state } } } ?: SmartResult.Err("no connection named “$conn”")

    suspend fun mqttPublish(conn: String, topic: String, payload: String, qos: Int, retain: Boolean): SmartResult<Unit> =
        entity(conn)?.let { e -> mqtt(e).then { it.publish(topic, payload, qos, retain) } } ?: SmartResult.Err("no connection named “$conn”")

    suspend fun hueLight(conn: String, light: String, on: Boolean?, bri: Int?): SmartResult<Unit> =
        entity(conn)?.let { e -> hue(e).then { it.setLight(light, on, bri) } } ?: SmartResult.Err("no connection named “$conn”")

    suspend fun hueScene(conn: String, scene: String, group: String): SmartResult<Unit> =
        entity(conn)?.let { e -> hue(e).then { it.recallScene(scene, group) } } ?: SmartResult.Err("no connection named “$conn”")

    suspend fun device(conn: String, on: Boolean?, channel: Int, bri: Int?): SmartResult<Unit> =
        entity(conn)?.let { e -> httpDevice(e).then { it.switch(on, channel, bri) } } ?: SmartResult.Err("no connection named “$conn”")
}

inline fun <A, B> SmartResult<A>.map(f: (A) -> B): SmartResult<B> = when (this) {
    is SmartResult.Ok -> SmartResult.Ok(f(value))
    is SmartResult.Err -> this
}

suspend inline fun <A, B> SmartResult<A>.then(crossinline f: suspend (A) -> SmartResult<B>): SmartResult<B> = when (this) {
    is SmartResult.Ok -> f(value)
    is SmartResult.Err -> this
}
