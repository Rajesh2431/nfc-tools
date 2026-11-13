package com.tapandgo.automation

import com.tapandgo.automation.actions.AndroidActions
import com.tapandgo.automation.actions.BuiltinVariables
import com.tapandgo.automation.engine.RunResult
import com.tapandgo.automation.engine.Workflow
import com.tapandgo.automation.engine.WorkflowEngine
import com.tapandgo.automation.engine.WorkflowJson
import com.tapandgo.data.database.WorkflowDao
import com.tapandgo.data.database.WorkflowEntity
import com.tapandgo.data.database.WorkflowRunEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class StoredWorkflow(
    val id: Long,
    val name: String,
    val description: String,
    val workflow: Workflow?,
    val triggers: WorkflowTriggers = WorkflowTriggers(),
)

/** What started a run. Only SCHEDULE runs without a visible activity. */
enum class RunSource { MANUAL, NFC_TAG, SHORTCUT, TILE, EXTERNAL, SCHEDULE }

/** Trigger configuration kept outside the workflow JSON (tokens are never exported). */
data class WorkflowTriggers(
    val token: String? = null,
    val autoRun: Boolean = false,
    val allowExternal: Boolean = false,
    val scheduleMinutes: Int = 0,
) {
    companion object {
        /** WorkManager's minimum periodic interval is 15 minutes. */
        val SCHEDULE_CHOICES = listOf(0, 15, 30, 60, 180, 360, 720, 1440)
        fun isValidSchedule(m: Int) = m in SCHEDULE_CHOICES
    }
}

internal fun WorkflowEntity.triggers() = WorkflowTriggers(triggerToken, autoRun, allowExternal, scheduleMinutes)

@Singleton
class WorkflowRepository @Inject constructor(
    private val dao: WorkflowDao,
    private val actions: AndroidActions,
    private val builtins: BuiltinVariables,
    private val scheduler: WorkflowScheduler,
) {
    private val now: () -> Long = System::currentTimeMillis
    val allowedActions: Set<String> get() = actions.registry.ids

    fun observeAll(): Flow<List<StoredWorkflow>> = dao.observeAll().map { rows ->
        rows.map { StoredWorkflow(it.id, it.name, it.description, WorkflowJson.decode(it.workflowJson, allowedActions), it.triggers()) }
    }

    suspend fun load(id: Long): Pair<WorkflowEntity, Workflow?>? = dao.byId(id)?.let { it to WorkflowJson.decode(it.workflowJson, allowedActions) }

    /** @return the id, or null when the workflow fails validation (nothing saved). */
    suspend fun count(): Int = dao.all().size

    suspend fun save(id: Long, w: Workflow, description: String): Long? {
        if (WorkflowJson.validate(w, allowedActions) != null || description.length > 500) return null
        val t = now()
        val json = WorkflowJson.encode(w)
        return if (id == 0L) {
            dao.insert(WorkflowEntity(name = w.name.trim(), description = description.trim(), workflowJson = json, createdAt = t, updatedAt = t))
        } else {
            val e = dao.byId(id) ?: return null
            dao.update(e.copy(name = w.name.trim(), description = description.trim(), workflowJson = json, updatedAt = t)); id
        }
    }

    suspend fun duplicate(id: Long, suffix: String) {
        val e = dao.byId(id) ?: return
        val t = now()
        // Triggers are not copied: a token must identify exactly one workflow.
        dao.insert(e.copy(id = 0, name = (e.name + " " + suffix).take(80), createdAt = t, updatedAt = t, triggerToken = null, autoRun = false, allowExternal = false, scheduleMinutes = 0))
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        dao.delete(id)
    }

    suspend fun byToken(token: String): StoredWorkflow? = dao.byToken(token)?.let {
        StoredWorkflow(it.id, it.name, it.description, WorkflowJson.decode(it.workflowJson, allowedActions), it.triggers())
    }

    /** Saves trigger settings; creates the token on first use and (re)schedules background runs. */
    suspend fun setTriggers(id: Long, autoRun: Boolean, allowExternal: Boolean, scheduleMinutes: Int, needToken: Boolean = false): WorkflowTriggers? {
        val e = dao.byId(id) ?: return null
        if (!WorkflowTriggers.isValidSchedule(scheduleMinutes)) return null
        val token = e.triggerToken ?: if (needToken || autoRun || allowExternal) java.util.UUID.randomUUID().toString() else null
        dao.updateTriggers(id, token, autoRun, allowExternal, scheduleMinutes)
        if (scheduleMinutes > 0) scheduler.schedule(id, scheduleMinutes) else scheduler.cancel(id)
        return WorkflowTriggers(token, autoRun, allowExternal, scheduleMinutes)
    }

    /** Returns the trigger token, creating one if needed (for tags, shortcuts and the tile). */
    suspend fun ensureToken(id: Long): String? {
        val e = dao.byId(id) ?: return null
        e.triggerToken?.let { return it }
        return setTriggers(id, e.autoRun, e.allowExternal, e.scheduleMinutes, needToken = true)?.token
    }

    suspend fun exportAll(): String = WorkflowTransfer.encode(
        dao.all().mapNotNull { e -> WorkflowJson.decode(e.workflowJson, allowedActions)?.let { WorkflowDto(e.description, it) } },
    )

    /** @return imported and skipped counts, or null if the file is not a workflow export. */
    suspend fun import(text: String): Pair<Int, Int>? {
        val parsed = WorkflowTransfer.decode(text, allowedActions) ?: return null
        var n = 0
        parsed.valid.forEach { if (save(0, it.workflow, it.description) != null) n++ }
        return n to (parsed.skipped + parsed.valid.size - n)
    }

    /** Re-registers schedules after an import/restore or app update. */
    suspend fun syncSchedules() {
        dao.scheduled().forEach { if (WorkflowTriggers.isValidSchedule(it.scheduleMinutes)) scheduler.schedule(it.id, it.scheduleMinutes) }
    }

    fun observeRuns(limit: Int = 50) = dao.observeRuns(limit)
    suspend fun clearRuns() = dao.clearRuns()

    /** Runs a stored workflow and records the outcome (step statuses only, secrets masked). */
    suspend fun run(id: Long, source: RunSource = RunSource.MANUAL): RunResult? {
        val (entity, wf) = load(id) ?: return null
        val workflow = wf ?: return null
        val secrets = workflow.variables.filterKeys { it.contains("PASSWORD") || it.contains("SECRET") || it.contains("TOKEN") }.values
        val engine = WorkflowEngine(actions.registry, builtins = { builtins.resolve(it) }, secrets = { secrets })
        val start = now()
        val result = engine.run(workflow)
        val details = Json.encodeToString(
            ListSerializer(String.serializer()),
            result.log.map { "${it.path} ${it.actionId} ${it.status}" + (it.detail?.let { d -> ": " + d.take(200) } ?: "") },
        )
        dao.insertRun(WorkflowRunEntity(workflowId = entity.id, workflowName = entity.name, startTime = start, endTime = now(), status = result.status.name, errorMessage = result.error?.take(300), detailsJson = details, source = source.name))
        return result
    }
}
