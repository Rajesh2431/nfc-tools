package com.tapandgo.domain.task

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Strict JSON for stored/imported actions and triggers. Data only, never code. */
object TaskJson {
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encodeActions(actions: List<TaskAction>): String = json.encodeToString(ListSerializer(TaskAction.serializer()), actions)

    fun decodeActions(text: String): List<TaskAction>? =
        runCatching { json.decodeFromString(ListSerializer(TaskAction.serializer()), text) }.getOrNull()

    fun encodeTrigger(trigger: TaskTrigger?): String? = trigger?.let { json.encodeToString(TaskTrigger.serializer(), it) }

    fun decodeTrigger(text: String?): Result<TaskTrigger?> =
        if (text == null) Result.success(null) else runCatching { json.decodeFromString(TaskTrigger.serializer(), text) }
}
