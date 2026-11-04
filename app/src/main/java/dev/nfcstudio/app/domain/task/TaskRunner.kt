package dev.nfcstudio.app.domain.task

/** Outcome of one action, logged locally without parameters (privacy). */
enum class ActionOutcome { DONE, SKIPPED, FAILED, CANCELLED }

/** Why performing an action failed; categories only, never payloads. */
enum class FailureReason { NO_APP, APP_NOT_FOUND, BLOCKED, ERROR }

/** Performs external actions. The Android implementation builds safe intents. */
fun interface ActionPerformer {
    /** @return null on success, else the failure reason. */
    fun perform(action: TaskAction): FailureReason?
}

data class LogEntry(val actionIndex: Int, val action: TaskAction, val outcome: ActionOutcome, val reason: FailureReason? = null)

/**
 * Pure, step-by-step execution of a profile. An action needing confirmation
 * cannot run until [confirm] is called; in-app actions are shown until the
 * user continues. Every terminal step is reported to [onLog].
 */
class TaskRunner(
    val profile: TaskProfile,
    private val performer: ActionPerformer,
    private val onLog: (LogEntry) -> Unit,
) {
    sealed interface State {
        data class Confirm(val index: Int, val action: TaskAction) : State
        data class Showing(val index: Int, val action: TaskAction) : State
        data class Failed(val index: Int, val action: TaskAction, val reason: FailureReason) : State
        data class Finished(val log: List<LogEntry>) : State
    }

    private val log = mutableListOf<LogEntry>()
    var state: State = State.Finished(emptyList())
        private set

    init {
        state = if (!profile.enabled || TaskValidator.validate(profile) != null) {
            State.Finished(emptyList())
        } else {
            enter(0)
        }
    }

    private fun enter(index: Int): State {
        if (index >= profile.actions.size) return State.Finished(log.toList())
        val action = profile.actions[index]
        return if (action.requiresConfirmation(profile.policy)) State.Confirm(index, action) else run(index, action)
    }

    private fun run(index: Int, action: TaskAction): State {
        if (!action.isExternal) return State.Showing(index, action)
        // Defence in depth: re-validate right before performing.
        val reason = if (TaskValidator.validate(action) != null) FailureReason.BLOCKED else performer.perform(action)
        return if (reason == null) {
            record(index, action, ActionOutcome.DONE)
            enter(index + 1)
        } else {
            State.Failed(index, action, reason)
        }
    }

    private fun record(index: Int, action: TaskAction, outcome: ActionOutcome, reason: FailureReason? = null) {
        val entry = LogEntry(index, action, outcome, reason)
        log += entry
        onLog(entry)
    }

    /** User approved the action awaiting confirmation. */
    fun confirm() {
        val s = state as? State.Confirm ?: return
        state = run(s.index, s.action)
    }

    /** In-app display acknowledged. */
    fun continueAfterShowing() {
        val s = state as? State.Showing ?: return
        record(s.index, s.action, ActionOutcome.DONE)
        state = enter(s.index + 1)
    }

    fun retry() {
        val s = state as? State.Failed ?: return
        state = run(s.index, s.action)
    }

    /** Skips the current action (from Confirm or Failed). */
    fun skip() {
        when (val s = state) {
            is State.Confirm -> {
                record(s.index, s.action, ActionOutcome.SKIPPED)
                state = enter(s.index + 1)
            }

            is State.Failed -> {
                record(s.index, s.action, ActionOutcome.FAILED, s.reason)
                state = enter(s.index + 1)
            }

            else -> Unit
        }
    }

    /** Stops the run; the current action is logged as cancelled. */
    fun cancel() {
        val (index, action) = when (val s = state) {
            is State.Confirm -> s.index to s.action
            is State.Showing -> s.index to s.action
            is State.Failed -> s.index to s.action
            is State.Finished -> return
        }
        record(index, action, ActionOutcome.CANCELLED)
        state = State.Finished(log.toList())
    }
}
