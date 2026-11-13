package com.tapandgo.feature.smarthome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapandgo.smarthome.ConnectionDraft
import com.tapandgo.smarthome.ConnectionProblem
import com.tapandgo.smarthome.HaEntity
import com.tapandgo.smarthome.HueLight
import com.tapandgo.smarthome.HueScene
import com.tapandgo.smarthome.SmartConnection
import com.tapandgo.smarthome.SmartHomeRepository
import com.tapandgo.smarthome.SmartKind
import com.tapandgo.smarthome.SmartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartHomeViewModel @Inject constructor(private val repo: SmartHomeRepository) : ViewModel() {
    val connections: StateFlow<List<SmartConnection>?> = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    /** (text, isError) from the last test. */
    val message: StateFlow<Pair<String, Boolean>?> = _message.asStateFlow()
    private val _testing = MutableStateFlow<Long?>(null)
    val testing: StateFlow<Long?> = _testing.asStateFlow()

    fun test(c: SmartConnection) {
        _testing.value = c.id
        viewModelScope.launch {
            _message.value = when (val r = repo.test(c.id)) {
                is SmartResult.Ok -> "${c.name}: ${r.value}" to false
                is SmartResult.Err -> "${c.name}: ${r.reason}" to true
            }
            _testing.value = null
        }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun dismiss() { _message.value = null }

    private val _problem = MutableStateFlow<ConnectionProblem?>(null)
    val problem: StateFlow<ConnectionProblem?> = _problem.asStateFlow()

    fun save(d: ConnectionDraft, onSaved: () -> Unit) = viewModelScope.launch {
        val (id, p) = repo.save(d)
        _problem.value = p
        if (id != null) onSaved()
    }

    fun clearProblem() { _problem.value = null }
}

/** Browse and control one connection's devices. */
@HiltViewModel
class SmartDevicesViewModel @Inject constructor(private val repo: SmartHomeRepository, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: 0L
    private val _connection = MutableStateFlow<SmartConnection?>(null)
    val connection: StateFlow<SmartConnection?> = _connection.asStateFlow()
    private val _entities = MutableStateFlow<List<HaEntity>>(emptyList())
    val entities: StateFlow<List<HaEntity>> = _entities.asStateFlow()
    private val _lights = MutableStateFlow<List<HueLight>>(emptyList())
    val lights: StateFlow<List<HueLight>> = _lights.asStateFlow()
    private val _scenes = MutableStateFlow<List<HueScene>>(emptyList())
    val scenes: StateFlow<List<HueScene>> = _scenes.asStateFlow()
    private val _status = MutableStateFlow<Pair<String, Boolean>?>(null)
    val status: StateFlow<Pair<String, Boolean>?> = _status.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init { refresh() }

    private fun <T> SmartResult<T>.report(ok: String? = null, onOk: (T) -> Unit = {}) {
        when (this) {
            is SmartResult.Ok -> { onOk(value); ok?.let { _status.value = it to false } }
            is SmartResult.Err -> _status.value = reason to true
        }
    }

    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch { try { block() } finally { _busy.value = false } }
    }

    fun refresh() = work {
        val c = repo.byId(id) ?: return@work
        _connection.value = c
        when (c.kind) {
            SmartKind.HOME_ASSISTANT -> repo.haStates(id).report { _entities.value = it }
            SmartKind.HUE -> if (c.hasSecret) {
                repo.hueLights(id).report { _lights.value = it }
                repo.hueScenes(id).report { _scenes.value = it }
            }
            else -> Unit
        }
    }

    fun toggle(e: HaEntity) = work { repo.haToggle(id, e).report(); repo.haStates(id).report { _entities.value = it } }
    fun hue(light: String, on: Boolean?, bri: Int? = null) = work { repo.hueSet(id, light, on, bri).report(); repo.hueLights(id).report { _lights.value = it } }
    fun scene(s: HueScene) = work { repo.hueScene(id.toString(), s.id, s.group ?: "0").report(ok = s.name) }
    fun pair() = work { repo.pairHue(id).report(ok = "paired") ; _connection.value = repo.byId(id); if (_connection.value?.hasSecret == true) refreshLights() }
    private suspend fun refreshLights() { repo.hueLights(id).report { _lights.value = it }; repo.hueScenes(id).report { _scenes.value = it } }
    fun device(on: Boolean?, channel: Int = 0, bri: Int? = null) = work { repo.deviceSwitch(id, on, channel, bri).report(ok = "OK") }
    fun publish(topic: String, payload: String, qos: Int, retain: Boolean) = work {
        val c = _connection.value ?: return@work
        repo.mqttPublish(c.id.toString(), topic, payload, qos, retain).report(ok = "published")
    }
}
