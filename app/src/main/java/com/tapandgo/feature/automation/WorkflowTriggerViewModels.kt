package com.tapandgo.feature.automation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapandgo.automation.LaunchDecision
import com.tapandgo.automation.LaunchPolicy
import com.tapandgo.automation.RunSource
import com.tapandgo.automation.StoredWorkflow
import com.tapandgo.automation.WorkflowRepository
import com.tapandgo.automation.WorkflowTriggers
import com.tapandgo.automation.engine.RunResult
import com.tapandgo.core.ndef.RichRecords
import com.tapandgo.data.settings.SettingsRepository
import com.tapandgo.di.PendingWriteStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of a token-triggered run (tag, shortcut, tile, other app). */
sealed interface LaunchState {
    data object Loading : LaunchState
    data object NotFound : LaunchState
    data class Blocked(val name: String) : LaunchState
    data class Confirm(val workflow: StoredWorkflow, val source: RunSource) : LaunchState
    data class Running(val name: String) : LaunchState
    data class Done(val name: String, val result: RunResult) : LaunchState
    data class Cancelled(val name: String) : LaunchState
}

@HiltViewModel
class WorkflowLaunchViewModel @Inject constructor(
    private val repo: WorkflowRepository,
    private val settings: SettingsRepository,
    private val entitlements: com.tapandgo.billing.EntitlementRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val token: String = handle.get<String>("token").orEmpty()
    private val source: RunSource = handle.get<String>("source")?.let { runCatching { RunSource.valueOf(it) }.getOrNull() } ?: RunSource.EXTERNAL
    private val _state = MutableStateFlow<LaunchState>(LaunchState.Loading)
    val state: StateFlow<LaunchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val wf = if (RichRecords.isValidToken(token)) repo.byToken(token) else null
            if (wf?.workflow == null) { _state.value = LaunchState.NotFound; return@launch }
            val feature = when (source) {
                RunSource.SHORTCUT, RunSource.TILE, RunSource.WIDGET -> com.tapandgo.billing.PremiumFeature.SHORTCUTS_AND_TILE
                RunSource.EXTERNAL -> com.tapandgo.billing.PremiumFeature.EXTERNAL_TRIGGERS
                else -> null
            }
            if (feature != null && !entitlements.current().allows(feature)) { _state.value = LaunchState.Blocked(wf.name); return@launch }
            when (LaunchPolicy.decide(source, wf.triggers, settings.current().allowExternalTriggers)) {
                LaunchDecision.BLOCKED -> _state.value = LaunchState.Blocked(wf.name)
                LaunchDecision.CONFIRM -> _state.value = LaunchState.Confirm(wf, source)
                LaunchDecision.RUN -> execute(wf)
            }
        }
    }

    fun confirm() { (state.value as? LaunchState.Confirm)?.let { execute(it.workflow) } }
    fun cancel() { (state.value as? LaunchState.Confirm)?.let { _state.value = LaunchState.Cancelled(it.workflow.name) } }

    private fun execute(wf: StoredWorkflow) {
        _state.value = LaunchState.Running(wf.name)
        viewModelScope.launch {
            val r = repo.run(wf.id, source)
            _state.value = if (r == null) LaunchState.NotFound else LaunchState.Done(wf.name, r)
        }
    }
}

@HiltViewModel
class WorkflowTriggersViewModel @Inject constructor(
    private val repo: WorkflowRepository,
    private val settingsRepo: SettingsRepository,
    private val pending: PendingWriteStore,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: 0L
    private val _workflow = MutableStateFlow<StoredWorkflow?>(null)
    val workflow: StateFlow<StoredWorkflow?> = _workflow.asStateFlow()
    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()
    val externalEnabled: StateFlow<Boolean> = settingsRepo.settings.map { it.allowExternalTriggers }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val tileWorkflowId: StateFlow<Long> = settingsRepo.settings.map { it.tileWorkflowId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val onWidget: StateFlow<Boolean> = settingsRepo.widgetWorkflows.map { id in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _widgetFull = MutableStateFlow(false)
    val widgetFull: StateFlow<Boolean> = _widgetFull.asStateFlow()

    fun setOnWidget(context: android.content.Context, shown: Boolean) = viewModelScope.launch {
        if (shown) repo.ensureToken(id)
        _widgetFull.value = !settingsRepo.setWidgetWorkflow(id, shown)
        com.tapandgo.automation.WorkflowWidget.refresh(context)
        reload()
    }

    init { reload() }

    private fun reload() = viewModelScope.launch {
        val e = repo.load(id)
        if (e == null) _missing.value = true
        else _workflow.value = StoredWorkflow(e.first.id, e.first.name, e.first.description, e.second, WorkflowTriggers(e.first.triggerToken, e.first.autoRun, e.first.allowExternal, e.first.scheduleMinutes))
    }

    private fun update(f: (WorkflowTriggers) -> WorkflowTriggers) {
        val t = f(_workflow.value?.triggers ?: return)
        viewModelScope.launch {
            repo.setTriggers(id, t.autoRun, t.allowExternal, t.scheduleMinutes)
            reload()
        }
    }

    fun setAutoRun(v: Boolean) = update { it.copy(autoRun = v) }
    fun setAllowExternal(v: Boolean) = update { it.copy(allowExternal = v) }
    fun setSchedule(m: Int) = update { it.copy(scheduleMinutes = m) }
    fun enableExternalGlobally() = viewModelScope.launch { settingsRepo.update { it.copy(allowExternalTriggers = true) } }

    /** Hands a task-tag record carrying this workflow's token to the Write tab. */
    fun writeTag(onReady: () -> Unit) = viewModelScope.launch {
        val token = repo.ensureToken(id) ?: return@launch
        pending.records.value = listOfNotNull(RichRecords.taskTag(token))
        reload()
        onReady()
    }

    /** Ensures a token, then gives it to [use] (pin shortcut). */
    fun withToken(use: (String) -> Unit) = viewModelScope.launch {
        val token = repo.ensureToken(id) ?: return@launch
        reload()
        use(token)
    }

    fun useForTile() = viewModelScope.launch {
        repo.ensureToken(id) ?: return@launch
        settingsRepo.update { it.copy(tileWorkflowId = id) }
        reload()
    }
}
