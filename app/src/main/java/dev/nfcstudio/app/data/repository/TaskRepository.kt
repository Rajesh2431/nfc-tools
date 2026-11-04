package dev.nfcstudio.app.data.repository

import dev.nfcstudio.app.data.database.TaskDao
import dev.nfcstudio.app.data.database.TaskLogEntity
import dev.nfcstudio.app.data.database.TaskProfileEntity
import dev.nfcstudio.app.domain.task.ConfirmationPolicy
import dev.nfcstudio.app.domain.task.LogEntry
import dev.nfcstudio.app.domain.task.ProfileProblem
import dev.nfcstudio.app.domain.task.TaskAction
import dev.nfcstudio.app.domain.task.TaskJson
import dev.nfcstudio.app.domain.task.TaskProfile
import dev.nfcstudio.app.domain.task.TaskValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A stored profile; [profile] is null when the row no longer validates (shown as invalid, never run). */
data class StoredProfile(val id: Long, val name: String, val profile: TaskProfile?)

class TaskRepository(private val dao: TaskDao, private val now: () -> Long = System::currentTimeMillis) {

    fun observeProfiles(): Flow<List<StoredProfile>> = dao.observeProfiles().map { rows -> rows.map(::toStored) }

    suspend fun profile(id: Long): TaskProfile? = dao.profile(id)?.let(::toModel)

    /** Inserts (id == 0) or updates. @return problem, or null when saved. */
    suspend fun save(profile: TaskProfile): Pair<ProfileProblem?, Long> {
        TaskValidator.validate(profile)?.let { return it to profile.id }
        val t = now()
        return if (profile.id == 0L) {
            null to dao.insertProfile(toEntity(profile.copy(createdAt = t, updatedAt = t)))
        } else {
            val existing = dao.profile(profile.id)
            dao.updateProfile(toEntity(profile.copy(createdAt = existing?.createdAt ?: t, updatedAt = t)))
            null to profile.id
        }
    }

    /** Profile whose trigger is exactly this task-tag token (token validated by caller). */
    suspend fun byTaskToken(token: String): TaskProfile? =
        dao.profilesMentioning(dev.nfcstudio.app.data.database.escapeLike(token)).mapNotNull(::toModel)
            .firstOrNull { (it.trigger as? dev.nfcstudio.app.domain.task.TaskTrigger.TaskTag)?.token == token }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
    suspend fun delete(id: Long) = dao.deleteProfile(id)

    fun observeLog(limit: Int = 100) = dao.observeLog(limit)
    suspend fun clearLog() = dao.clearLog()

    suspend fun log(profile: TaskProfile, entry: LogEntry) = dao.insertLog(
        TaskLogEntity(
            profileId = profile.id,
            profileName = profile.name,
            actionIndex = entry.actionIndex,
            actionType = actionType(entry.action),
            outcome = entry.outcome.name,
            reason = entry.reason?.name,
            createdAt = now(),
        ),
    )

    companion object {
        fun actionType(action: TaskAction): String = when (action) {
            is TaskAction.OpenUrl -> "open_url"
            is TaskAction.OpenMap -> "open_map"
            is TaskAction.DisplayText -> "display_text"
            is TaskAction.DraftEmail -> "draft_email"
            is TaskAction.DraftSms -> "draft_sms"
            is TaskAction.ShowContact -> "show_contact"
            is TaskAction.OpenApp -> "open_app"
            is TaskAction.OpenSettings -> "open_settings"
        }

        fun toEntity(p: TaskProfile) = TaskProfileEntity(
            id = p.id,
            name = p.name.trim(),
            enabled = p.enabled,
            actionsJson = TaskJson.encodeActions(p.actions),
            policy = p.policy.name,
            triggerJson = TaskJson.encodeTrigger(p.trigger),
            createdAt = p.createdAt,
            updatedAt = p.updatedAt,
            autoRun = p.autoRun,
        )

        /** Decodes and re-validates; returns null for anything that does not pass. */
        fun toModel(row: TaskProfileEntity): TaskProfile? {
            val actions = TaskJson.decodeActions(row.actionsJson) ?: return null
            val policy = runCatching { ConfirmationPolicy.valueOf(row.policy) }.getOrNull() ?: return null
            val trigger = TaskJson.decodeTrigger(row.triggerJson).getOrElse { return null }
            val profile = TaskProfile(row.id, row.name, row.enabled, actions, policy, trigger, row.autoRun, row.createdAt, row.updatedAt)
            return profile.takeIf { TaskValidator.validate(it) == null }
        }

        private fun toStored(row: TaskProfileEntity) = StoredProfile(row.id, row.name, toModel(row))
    }
}
