package com.tapandgo.feature.automation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapandgo.automation.StoredWorkflow
import com.tapandgo.automation.WorkflowRepository
import com.tapandgo.automation.engine.Block
import com.tapandgo.automation.engine.RunResult
import com.tapandgo.automation.engine.Workflow
import com.tapandgo.automation.engine.WorkflowEdit
import com.tapandgo.automation.engine.WorkflowEdit.ListRef
import com.tapandgo.automation.engine.WorkflowJson
import com.tapandgo.data.database.WorkflowRunEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkflowsViewModel @Inject constructor(private val repo: WorkflowRepository) : ViewModel() {
    val workflows: StateFlow<List<StoredWorkflow>?> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val runs: StateFlow<List<WorkflowRunEntity>> = repo.observeRuns(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _running = MutableStateFlow<Long?>(null)
    val running: StateFlow<Long?> = _running.asStateFlow()
    private val _result = MutableStateFlow<Pair<String, RunResult>?>(null)
    val result: StateFlow<Pair<String, RunResult>?> = _result.asStateFlow()

    /** Manual execution: the user tapped Run on this workflow. */
    fun run(w: StoredWorkflow) {
        if (_running.value != null) return
        _running.value = w.id
        viewModelScope.launch {
            try {
                repo.run(w.id)?.let { _result.value = w.name to it }
            } finally {
                _running.value = null
            }
        }
    }

    fun dismissResult() { _result.value = null }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun duplicate(id: Long, suffix: String) = viewModelScope.launch { repo.duplicate(id, suffix) }
    fun clearRuns() = viewModelScope.launch { repo.clearRuns() }
}

@HiltViewModel
class WorkflowEditorViewModel @Inject constructor(
    private val repo: WorkflowRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private var id: Long = handle.get<Long>("id") ?: 0L
    private val _workflow = MutableStateFlow(Workflow(name = "", blocks = emptyList()))
    val workflow: StateFlow<Workflow> = _workflow.asStateFlow()
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()
    private val _problem = MutableStateFlow<WorkflowJson.Problem?>(null)
    val problem: StateFlow<WorkflowJson.Problem?> = _problem.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        if (id != 0L) viewModelScope.launch {
            repo.load(id)?.let { (e, w) -> _description.value = e.description; w?.let { _workflow.value = it } }
        }
    }

    fun rename(name: String) { _workflow.update { it.copy(name = name) }; _problem.value = null }
    fun describe(d: String) { _description.value = d.take(500) }
    fun setStopOnError(v: Boolean) = _workflow.update { it.copy(settings = it.settings.copy(stopOnError = v)) }

    private fun edit(f: (List<Block>) -> List<Block>) { _workflow.update { it.copy(blocks = f(it.blocks)) }; _problem.value = null; _saved.value = false }
    fun insert(ref: ListRef, b: Block) = edit { WorkflowEdit.insert(it, ref, b) }
    fun replace(ref: ListRef, i: Int, b: Block) = edit { WorkflowEdit.replace(it, ref, i, b) }
    fun remove(ref: ListRef, i: Int) = edit { WorkflowEdit.remove(it, ref, i) }
    fun move(ref: ListRef, i: Int, d: Int) = edit { WorkflowEdit.move(it, ref, i, d) }
    fun duplicate(ref: ListRef, i: Int) = edit { WorkflowEdit.duplicate(it, ref, i) }
    fun toggle(ref: ListRef, i: Int) = edit { WorkflowEdit.toggle(it, ref, i) }

    fun save() {
        val w = _workflow.value
        WorkflowJson.validate(w, repo.allowedActions)?.let { _problem.value = it; return }
        viewModelScope.launch {
            val saved = repo.save(id, w, _description.value)
            if (saved == null) _problem.value = WorkflowJson.Problem.NAME else { id = saved; _saved.value = true }
        }
    }
}
