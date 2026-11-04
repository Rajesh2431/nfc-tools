package com.tapandgo.feature.tasks

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.tapandgo.data.database.TaskLogEntity
import com.tapandgo.data.repository.StoredProfile
import com.tapandgo.data.repository.TaskRepository
import com.tapandgo.domain.task.ActionPerformer
import com.tapandgo.domain.task.ActionProblem
import com.tapandgo.domain.task.ConfirmationPolicy
import com.tapandgo.domain.task.ProfileProblem
import com.tapandgo.domain.task.SettingsPanel
import com.tapandgo.domain.task.TaskAction
import com.tapandgo.domain.task.TaskProfile
import com.tapandgo.domain.task.TaskRunner
import com.tapandgo.domain.task.TaskTrigger
import com.tapandgo.domain.task.TaskValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TasksViewModel @Inject constructor(private val repository: TaskRepository) : ViewModel() {
    val profiles: StateFlow<List<StoredProfile>?> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val log: StateFlow<List<TaskLogEntity>> = repository.observeLog(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch { repository.setEnabled(id, enabled) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun clearLog() = viewModelScope.launch { repository.clearLog() }
}

/** Editable fields of an action before validation. */
data class ActionDraft(
    val type: Type = Type.OPEN_URL,
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val panel: SettingsPanel = SettingsPanel.NFC,
) {
    enum class Type { OPEN_URL, OPEN_MAP, DISPLAY_TEXT, DRAFT_EMAIL, DRAFT_SMS, SHOW_CONTACT, OPEN_APP, OPEN_SETTINGS }

    fun toAction(): TaskAction = when (type) {
        Type.OPEN_URL -> TaskAction.OpenUrl(a.trim())
        Type.OPEN_MAP -> TaskAction.OpenMap(a.trim())
        Type.DISPLAY_TEXT -> TaskAction.DisplayText(a)
        Type.DRAFT_EMAIL -> TaskAction.DraftEmail(a.trim(), b, c)
        Type.DRAFT_SMS -> TaskAction.DraftSms(a.trim(), b)
        Type.SHOW_CONTACT -> TaskAction.ShowContact(a.trim(), b.trim(), c.trim())
        Type.OPEN_APP -> TaskAction.OpenApp(a.trim())
        Type.OPEN_SETTINGS -> TaskAction.OpenSettings(panel)
    }
}

enum class TriggerKind { NONE, TEXT, HOST, TASK_TAG }

data class ProfileDraft(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val policy: ConfirmationPolicy = ConfirmationPolicy.EXTERNAL_ONLY,
    val triggerKind: TriggerKind = TriggerKind.NONE,
    val triggerValue: String = "",
    val actions: List<TaskAction> = emptyList(),
    val autoRun: Boolean = false,
    /** Random token for task tags; generated once per profile. */
    val token: String = java.util.UUID.randomUUID().toString(),
) {
    fun toProfile() = TaskProfile(
        id = id,
        name = name,
        enabled = enabled,
        actions = actions,
        policy = policy,
        trigger = when (triggerKind) {
            TriggerKind.NONE -> null
            TriggerKind.TEXT -> TaskTrigger.TextContains(triggerValue.trim())
            TriggerKind.HOST -> TaskTrigger.UriHost(triggerValue.trim().lowercase())
            TriggerKind.TASK_TAG -> TaskTrigger.TaskTag(token)
        },
        autoRun = autoRun && triggerKind != TriggerKind.NONE && policy == ConfirmationPolicy.EXTERNAL_ONLY,
    )
}

@HiltViewModel
class ProfileEditorViewModel(
    private val repository: TaskRepository,
    profileId: Long,
    private val pending: com.tapandgo.di.PendingWriteStore = com.tapandgo.di.PendingWriteStore(),
) : ViewModel() {

    @Inject constructor(repository: TaskRepository, pending: com.tapandgo.di.PendingWriteStore, handle: androidx.lifecycle.SavedStateHandle) :
        this(repository, handle.get<Long>("id") ?: 0L, pending)

    /** Saves the profile, then hands its task-tag record to the Write tab. */
    fun writeTaskTag(onReady: () -> Unit) {
        val token = _draft.value?.token ?: return
        save {
            pending.records.value = listOfNotNull(com.tapandgo.core.ndef.RichRecords.taskTag(token))
            onReady()
        }
    }

    private val _draft = MutableStateFlow<ProfileDraft?>(if (profileId == 0L) ProfileDraft() else null)
    val draft: StateFlow<ProfileDraft?> = _draft.asStateFlow()
    private val _actionDraft = MutableStateFlow(ActionDraft())
    val actionDraft: StateFlow<ActionDraft> = _actionDraft.asStateFlow()
    private val _actionProblem = MutableStateFlow<ActionProblem?>(null)
    val actionProblem: StateFlow<ActionProblem?> = _actionProblem.asStateFlow()
    private val _problem = MutableStateFlow<ProfileProblem?>(null)
    val problem: StateFlow<ProfileProblem?> = _problem.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()
    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    init {
        if (profileId != 0L) {
            viewModelScope.launch {
                val p = repository.profile(profileId)
                if (p == null) {
                    _missing.value = true
                } else {
                    _draft.value = ProfileDraft(
                        id = p.id, name = p.name, enabled = p.enabled, policy = p.policy, actions = p.actions, autoRun = p.autoRun,
                        token = (p.trigger as? TaskTrigger.TaskTag)?.token ?: java.util.UUID.randomUUID().toString(),
                        triggerKind = when (p.trigger) {
                            null -> TriggerKind.NONE
                            is TaskTrigger.TextContains -> TriggerKind.TEXT
                            is TaskTrigger.UriHost -> TriggerKind.HOST
                            is TaskTrigger.TaskTag -> TriggerKind.TASK_TAG
                        },
                        triggerValue = when (val t = p.trigger) {
                            null -> ""
                            is TaskTrigger.TextContains -> t.value
                            is TaskTrigger.UriHost -> t.host
                            is TaskTrigger.TaskTag -> ""
                        },
                    )
                }
            }
        }
    }

    fun update(transform: (ProfileDraft) -> ProfileDraft) {
        _draft.update { it?.let(transform) }
        _problem.value = null
    }

    fun updateAction(transform: (ActionDraft) -> ActionDraft) {
        _actionDraft.update(transform)
        _actionProblem.value = null
    }

    fun addAction(): Boolean {
        val action = _actionDraft.value.toAction()
        TaskValidator.validate(action)?.let {
            _actionProblem.value = it
            return false
        }
        if ((_draft.value?.actions?.size ?: 0) >= TaskValidator.MAX_ACTIONS) {
            _problem.value = ProfileProblem.TOO_MANY_ACTIONS
            return false
        }
        update { it.copy(actions = it.actions + action) }
        _actionDraft.value = ActionDraft(type = _actionDraft.value.type)
        return true
    }

    fun removeAction(index: Int) = update { d -> d.copy(actions = d.actions.filterIndexed { i, _ -> i != index }) }

    fun moveAction(index: Int, delta: Int) = update { d ->
        val list = d.actions.toMutableList()
        val target = index + delta
        if (index in list.indices && target in list.indices) list.add(target, list.removeAt(index))
        d.copy(actions = list)
    }

    /** Saves, then reports success via [saved]; [afterSave] runs once the row exists. */
    fun save(afterSave: (() -> Unit)? = null) {
        val d = _draft.value ?: return
        val profile = d.toProfile()
        TaskValidator.validate(profile)?.let {
            _problem.value = it
            return
        }
        viewModelScope.launch {
            val (problem, id) = repository.save(profile)
            _problem.value = problem
            if (problem == null) {
                _draft.update { it?.copy(id = id) }
                if (afterSave != null) afterSave() else _saved.value = true
            }
        }
    }
}

@HiltViewModel
class TaskRunViewModel(
    private val repository: TaskRepository,
    private val performer: ActionPerformer,
    profileId: Long,
    private val autoStarted: Boolean = false,
    private val contactSaver: (TaskAction.ShowContact) -> com.tapandgo.domain.task.FailureReason? = { com.tapandgo.domain.task.FailureReason.NO_APP },
) : ViewModel() {

    @Inject constructor(repository: TaskRepository, performer: IntentActionPerformer, handle: androidx.lifecycle.SavedStateHandle) :
        this(repository, performer, handle.get<Long>("id") ?: 0L, handle.get<Boolean>("auto") ?: false, performer::saveContact)

    /** Opens the system "insert contact" screen; the user saves there. */
    fun saveContact(action: TaskAction.ShowContact): Boolean = contactSaver(action) == null

    sealed interface UiState {
        data object Loading : UiState
        data object Unavailable : UiState
        data class Running(val profile: TaskProfile, val state: TaskRunner.State) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var runner: TaskRunner? = null

    init {
        viewModelScope.launch {
            val profile = repository.profile(profileId)
            if (profile == null || !profile.enabled) {
                _state.value = UiState.Unavailable
                return@launch
            }
            val r = TaskRunner(profile, performer, { entry -> viewModelScope.launch { repository.log(profile, entry) } }, autoStarted)
            runner = r
            publish()
        }
    }

    private fun publish() {
        val r = runner ?: return
        _state.value = UiState.Running(r.profile, r.state)
    }

    fun confirm() { runner?.confirm(); publish() }
    fun continueAfterShowing() { runner?.continueAfterShowing(); publish() }
    fun retry() { runner?.retry(); publish() }
    fun skip() { runner?.skip(); publish() }
    fun cancel() { runner?.cancel(); publish() }
}
