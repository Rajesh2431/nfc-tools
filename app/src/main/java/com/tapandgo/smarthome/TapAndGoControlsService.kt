package com.tapandgo.smarthome

import android.app.PendingIntent
import android.content.Intent
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.StatelessTemplate
import android.service.controls.templates.ToggleTemplate
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.tapandgo.MainActivity
import com.tapandgo.automation.RunSource
import com.tapandgo.automation.WorkflowRepository
import com.tapandgo.automation.actions.BackgroundRun
import com.tapandgo.billing.EntitlementRepository
import com.tapandgo.billing.PremiumFeature
import com.tapandgo.data.database.SmartHomeDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Flow
import java.util.function.Consumer

/** Control ids: "ha:<conn>:<entity>", "hue:<conn>:<light>", "dev:<conn>", "wf:<workflow>". Pure for tests. */
object ControlIds {
    sealed interface Parsed {
        data class Ha(val conn: Long, val entity: String) : Parsed
        data class Hue(val conn: Long, val light: String) : Parsed
        data class Device(val conn: Long) : Parsed
        data class Workflow(val id: Long) : Parsed
    }

    fun ha(conn: Long, entity: String) = "ha:$conn:$entity"
    fun hue(conn: Long, light: String) = "hue:$conn:$light"
    fun device(conn: Long) = "dev:$conn"
    fun workflow(id: Long) = "wf:$id"

    fun parse(id: String): Parsed? {
        val p = id.split(':', limit = 3)
        return when (p[0]) {
            "ha" -> if (p.size == 3) p[1].toLongOrNull()?.let { c -> p[2].takeIf(HomeAssistantClient::isEntityId)?.let { Parsed.Ha(c, it) } } else null
            "hue" -> if (p.size == 3) p[1].toLongOrNull()?.let { c -> p[2].takeIf { it.matches(Regex("^[A-Za-z0-9-]{1,64}$")) }?.let { Parsed.Hue(c, it) } } else null
            "dev" -> if (p.size == 2) p[1].toLongOrNull()?.let { Parsed.Device(it) } else null
            "wf" -> if (p.size == 2) p[1].toLongOrNull()?.let { Parsed.Workflow(it) } else null
            else -> null
        }
    }
}

/**
 * Android Device Controls (Android 11+): smart-home toggles and workflow
 * buttons in the system controls panel. States are read live from the
 * device; a failed read shows the control as an error, never a guess.
 */
@RequiresApi(30)
class TapAndGoControlsService : ControlsProviderService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun smartHome(): SmartHomeRepository
        fun smartDao(): SmartHomeDao
        fun workflows(): WorkflowRepository
        fun entitlements(): EntitlementRepository
    }

    private val deps by lazy { EntryPointAccessors.fromApplication(applicationContext, Deps::class.java) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Live subscribers for createPublisherFor, so actions can push new state. */
    private val live = ConcurrentHashMap<String, MutableSet<Flow.Subscriber<in Control>>>()

    private val appIntent by lazy {
        PendingIntent.getActivity(
            this, 0,
            Intent(Intent.ACTION_VIEW, "tapandgo://smarthome".toUri(), this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> = ListPublisher(scope) {
        buildList {
            val repo = deps.smartHome()
            for (c in repo.all()) {
                when (c.kind) {
                    SmartKind.HOME_ASSISTANT -> (repo.haStates(c.id) as? SmartResult.Ok)?.value?.filter { it.toggleable }?.take(100)?.forEach { e ->
                        add(stateless(ControlIds.ha(c.id, e.entityId), e.friendlyName, c.name, haType(e.domain)))
                    }
                    SmartKind.HUE -> (repo.hueLights(c.id) as? SmartResult.Ok)?.value?.forEach { l ->
                        add(stateless(ControlIds.hue(c.id, l.id), l.name, c.name, DeviceTypes.TYPE_LIGHT))
                    }
                    SmartKind.MQTT -> Unit
                    else -> add(stateless(ControlIds.device(c.id), c.name, c.address, DeviceTypes.TYPE_SWITCH))
                }
            }
            if (deps.entitlements().current().allows(PremiumFeature.SHORTCUTS_AND_TILE)) {
                deps.workflows().observeAll().first().filter { it.workflow != null }.forEach { w ->
                    add(stateless(ControlIds.workflow(w.id), w.name, getString(com.tapandgo.R.string.wf_title), DeviceTypes.TYPE_ROUTINE))
                }
            }
        }
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> = Flow.Publisher { sub ->
        controlIds.forEach { id -> live.getOrPut(id) { ConcurrentHashMap.newKeySet() }.add(sub) }
        sub.onSubscribe(object : Flow.Subscription {
            override fun request(n: Long) {
                scope.launch { controlIds.forEach { id -> sub.onNext(current(id)) } }
            }
            override fun cancel() { live.values.forEach { it.remove(sub) } }
        })
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        consumer.accept(ControlAction.RESPONSE_OK)
        scope.launch {
            val repo = deps.smartHome()
            val want = (action as? BooleanAction)?.newState
            when (val p = ControlIds.parse(controlId)) {
                is ControlIds.Parsed.Ha -> {
                    val e = (repo.haStates(p.conn) as? SmartResult.Ok)?.value?.firstOrNull { it.entityId == p.entity }
                    if (e != null) repo.haToggle(p.conn, e)
                }
                is ControlIds.Parsed.Hue -> repo.hueSet(p.conn, p.light, want, null)
                is ControlIds.Parsed.Device -> repo.deviceSwitch(p.conn, want)
                is ControlIds.Parsed.Workflow -> if (deps.entitlements().current().allows(PremiumFeature.SHORTCUTS_AND_TILE)) {
                    // Runs without a visible screen: actions that open screens report Unsupported.
                    withContext(BackgroundRun) { deps.workflows().run(p.id, RunSource.DEVICE_CONTROL) }
                }
                null -> Unit
            }
            val updated = current(controlId)
            live[controlId]?.forEach { it.onNext(updated) }
        }
    }

    private suspend fun current(id: String): Control {
        val repo = deps.smartHome()
        return when (val p = ControlIds.parse(id)) {
            is ControlIds.Parsed.Ha -> {
                val conn = repo.byId(p.conn)
                when (val r = repo.haStates(p.conn)) {
                    is SmartResult.Ok -> r.value.firstOrNull { it.entityId == p.entity }?.let { e ->
                        val on = e.state in setOf("on", "open", "unlocked", "playing", "heat", "cool", "auto")
                        toggle(id, e.friendlyName, conn?.name.orEmpty(), haType(e.domain), on, e.state)
                    } ?: status(id, p.entity, Control.STATUS_NOT_FOUND)
                    is SmartResult.Err -> status(id, p.entity, Control.STATUS_ERROR, r.reason)
                }
            }
            is ControlIds.Parsed.Hue -> when (val r = repo.hueLights(p.conn)) {
                is SmartResult.Ok -> r.value.firstOrNull { it.id == p.light }?.let { l ->
                    toggle(id, l.name, repo.byId(p.conn)?.name.orEmpty(), DeviceTypes.TYPE_LIGHT, l.on, if (l.reachable) (if (l.on) "On" else "Off") else "Unreachable")
                } ?: status(id, p.light, Control.STATUS_NOT_FOUND)
                is SmartResult.Err -> status(id, p.light, Control.STATUS_ERROR, r.reason)
            }
            is ControlIds.Parsed.Device -> repo.byId(p.conn)?.let { c ->
                // These devices are switched blind (no state read), so the control is a button, not a toggle.
                Control.StatefulBuilder(id, appIntent).setTitle(c.name).setSubtitle(c.address).setDeviceType(DeviceTypes.TYPE_SWITCH)
                    .setStatus(Control.STATUS_OK).setControlTemplate(StatelessTemplate(id)).setStatusText(getString(com.tapandgo.R.string.sh_tap_toggle)).build()
            } ?: status(id, id, Control.STATUS_NOT_FOUND)
            is ControlIds.Parsed.Workflow -> {
                val w = deps.workflows().observeAll().first().firstOrNull { it.id == p.id }
                if (w == null) status(id, id, Control.STATUS_NOT_FOUND)
                else Control.StatefulBuilder(id, appIntent).setTitle(w.name).setSubtitle(getString(com.tapandgo.R.string.wf_title))
                    .setDeviceType(DeviceTypes.TYPE_ROUTINE).setStatus(Control.STATUS_OK).setControlTemplate(StatelessTemplate(id)).build()
            }
            null -> status(id, id, Control.STATUS_NOT_FOUND)
        }
    }

    private fun stateless(id: String, title: String, subtitle: String, type: Int) =
        Control.StatelessBuilder(id, appIntent).setTitle(title).setSubtitle(subtitle).setDeviceType(type).build()

    private fun toggle(id: String, title: String, subtitle: String, type: Int, on: Boolean, statusText: String) =
        Control.StatefulBuilder(id, appIntent).setTitle(title).setSubtitle(subtitle).setDeviceType(type).setStatus(Control.STATUS_OK)
            .setStatusText(statusText).setControlTemplate(ToggleTemplate(id, ControlButton(on, title))).build()

    private fun status(id: String, title: String, status: Int, text: String? = null) =
        Control.StatefulBuilder(id, appIntent).setTitle(title).setStatus(status).apply { text?.let { setStatusText(it.take(60)) } }.build()

    private fun haType(domain: String) = when (domain) {
        "light" -> DeviceTypes.TYPE_LIGHT
        "fan" -> DeviceTypes.TYPE_FAN
        "lock" -> DeviceTypes.TYPE_LOCK
        "cover" -> DeviceTypes.TYPE_BLINDS
        "climate" -> DeviceTypes.TYPE_THERMOSTAT
        "media_player" -> DeviceTypes.TYPE_TV
        else -> DeviceTypes.TYPE_SWITCH
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** Emits a computed list once, then completes (createPublisherForAllAvailable contract). */
@RequiresApi(30)
private class ListPublisher(private val scope: CoroutineScope, private val produce: suspend () -> List<Control>) : Flow.Publisher<Control> {
    override fun subscribe(sub: Flow.Subscriber<in Control>) {
        var started = false
        sub.onSubscribe(object : Flow.Subscription {
            override fun request(n: Long) {
                if (started) return
                started = true
                scope.launch {
                    runCatching { produce() }.onSuccess { list -> list.forEach(sub::onNext); sub.onComplete() }.onFailure { sub.onError(it) }
                }
            }
            override fun cancel() = Unit
        })
    }
}
