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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkflowsViewModel @Inject constructor(
    private val repo: WorkflowRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    entitlements: com.tapandgo.billing.EntitlementRepository,
) : ViewModel() {
    val premium: StateFlow<Boolean> = entitlements.entitlement.map { it.premium }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    /** Result of the last import/export: null, or a message resource with counts. */
    private val _transfer = MutableStateFlow<Pair<Int, List<Int>>?>(null)
    val transfer: StateFlow<Pair<Int, List<Int>>?> = _transfer.asStateFlow()
    fun dismissTransfer() { _transfer.value = null }

    fun export(target: android.net.Uri) = viewModelScope.launch {
        val text = repo.exportAll()
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(target, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess
        }
        _transfer.value = (if (ok) com.tapandgo.R.string.wf_export_done else com.tapandgo.R.string.wf_export_failed) to emptyList()
    }

    fun import(source: android.net.Uri) = viewModelScope.launch {
        val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(source)!!.use { input ->
                    val bytes = input.readNBytesCompat(com.tapandgo.automation.WorkflowTransfer.MAX_BYTES + 1)
                    if (bytes.size > com.tapandgo.automation.WorkflowTransfer.MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }
        val r = text?.let { repo.import(it) }
        _transfer.value = if (r == null) com.tapandgo.R.string.wf_import_invalid to emptyList() else com.tapandgo.R.string.wf_import_done to listOf(r.first, r.second)
    }

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
    private val entitlements: com.tapandgo.billing.EntitlementRepository,
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
    private val _limit = MutableStateFlow(false)
    /** Saving a new workflow was refused by the free limit. */
    val limit: StateFlow<Boolean> = _limit.asStateFlow()

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
            if (id == 0L && !entitlements.current().premium && repo.count() >= com.tapandgo.billing.Entitlement.FREE_WORKFLOW_LIMIT) {
                _problem.value = WorkflowJson.Problem.NAME; _limit.value = true; return@launch
            }
            val saved = repo.save(id, w, _description.value)
            if (saved == null) _problem.value = WorkflowJson.Problem.NAME else { id = saved; _saved.value = true }
        }
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes is API 33). */
internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
