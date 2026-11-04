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

data class StoredWorkflow(val id: Long, val name: String, val description: String, val workflow: Workflow?)

@Singleton
class WorkflowRepository @Inject constructor(
    private val dao: WorkflowDao,
    private val actions: AndroidActions,
    private val builtins: BuiltinVariables,
) {
    private val now: () -> Long = System::currentTimeMillis
    val allowedActions: Set<String> get() = actions.registry.ids

    fun observeAll(): Flow<List<StoredWorkflow>> = dao.observeAll().map { rows ->
        rows.map { StoredWorkflow(it.id, it.name, it.description, WorkflowJson.decode(it.workflowJson, allowedActions)) }
    }

    suspend fun load(id: Long): Pair<WorkflowEntity, Workflow?>? = dao.byId(id)?.let { it to WorkflowJson.decode(it.workflowJson, allowedActions) }

    /** @return the id, or null when the workflow fails validation (nothing saved). */
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
        dao.insert(e.copy(id = 0, name = (e.name + " " + suffix).take(80), createdAt = t, updatedAt = t))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    fun observeRuns(limit: Int = 50) = dao.observeRuns(limit)
    suspend fun clearRuns() = dao.clearRuns()

    /** Runs a stored workflow and records the outcome (step statuses only, secrets masked). */
    suspend fun run(id: Long): RunResult? {
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
        dao.insertRun(WorkflowRunEntity(workflowId = entity.id, workflowName = entity.name, startTime = start, endTime = now(), status = result.status.name, errorMessage = result.error?.take(300), detailsJson = details))
        return result
    }
}
