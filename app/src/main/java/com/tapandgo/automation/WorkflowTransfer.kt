package com.tapandgo.automation

import com.tapandgo.automation.engine.Workflow
import com.tapandgo.automation.engine.WorkflowJson
import kotlinx.serialization.Serializable

/**
 * Portable workflow file. Trigger tokens, auto-run, external access and
 * schedules are deliberately not exported: an imported file can never make a
 * workflow run on its own.
 */
@Serializable
data class WorkflowFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val workflows: List<WorkflowDto>,
) {
    companion object { const val FORMAT = "tapandgo-workflows" }
}

@Serializable
data class WorkflowDto(val description: String = "", val workflow: Workflow)

object WorkflowTransfer {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_WORKFLOWS = 200

    data class Parsed(val valid: List<WorkflowDto>, val skipped: Int)

    fun encode(items: List<WorkflowDto>): String = WorkflowJson.json.encodeToString(WorkflowFile.serializer(), WorkflowFile(workflows = items))

    /** Untrusted input: size, format and every workflow are checked; invalid entries are skipped. */
    fun decode(text: String, allowed: Set<String>): Parsed? {
        if (text.length > MAX_BYTES) return null
        val file = runCatching { WorkflowJson.json.decodeFromString(WorkflowFile.serializer(), text) }.getOrNull() ?: return null
        if (file.format != WorkflowFile.FORMAT || file.version != 1 || file.workflows.size > MAX_WORKFLOWS) return null
        val (ok, bad) = file.workflows.partition { WorkflowJson.validate(it.workflow, allowed) == null && it.description.length <= 500 }
        return Parsed(ok, bad.size)
    }
}
