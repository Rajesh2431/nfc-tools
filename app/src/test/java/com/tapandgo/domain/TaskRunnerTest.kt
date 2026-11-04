package com.tapandgo.domain

import com.tapandgo.domain.task.ActionOutcome
import com.tapandgo.domain.task.ActionPerformer
import com.tapandgo.domain.task.ConfirmationPolicy
import com.tapandgo.domain.task.FailureReason
import com.tapandgo.domain.task.LogEntry
import com.tapandgo.domain.task.SettingsPanel
import com.tapandgo.domain.task.TaskAction
import com.tapandgo.domain.task.TaskProfile
import com.tapandgo.domain.task.TaskRunner
import com.tapandgo.domain.task.TaskRunner.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRunnerTest {

    private val performed = mutableListOf<TaskAction>()
    private val logged = mutableListOf<LogEntry>()
    private var failWith: FailureReason? = null
    private val performer = ActionPerformer { performed += it; failWith }

    private fun runner(
        vararg actions: TaskAction,
        policy: ConfirmationPolicy = ConfirmationPolicy.EXTERNAL_ONLY,
        enabled: Boolean = true,
        autoRun: Boolean = false,
        autoStarted: Boolean = false,
    ) = TaskRunner(
        TaskProfile(id = 1, name = "p", enabled = enabled, actions = actions.toList(), policy = policy, autoRun = autoRun),
        performer,
        { logged += it },
        autoStarted,
    )

    @Test
    fun `auto-started runs skip confirmation only for safe actions`() {
        val settings = TaskAction.OpenSettings(SettingsPanel.WIFI)
        val r = runner(settings, url, autoRun = true, autoStarted = true)
        // Settings screen opened without asking; the URL still waits for confirmation.
        assertEquals(listOf<TaskAction>(settings), performed)
        assertEquals(State.Confirm(1, url), r.state)
    }

    @Test
    fun `auto-start without the profile opt-in still confirms`() {
        val r = runner(TaskAction.OpenSettings(SettingsPanel.WIFI), autoRun = false, autoStarted = true)
        assertTrue(r.state is State.Confirm)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun `every-action policy beats auto-run`() {
        val r = runner(TaskAction.OpenSettings(SettingsPanel.WIFI), policy = ConfirmationPolicy.EVERY_ACTION, autoRun = true, autoStarted = true)
        assertTrue(r.state is State.Confirm)
    }

    private val url = TaskAction.OpenUrl("https://example.com")
    private val text = TaskAction.DisplayText("hi")

    @Test
    fun `external actions never run without confirmation`() {
        val r = runner(url)
        assertTrue(r.state is State.Confirm)
        assertTrue(performed.isEmpty())
        r.confirm()
        assertEquals(listOf<TaskAction>(url), performed)
        assertTrue(r.state is State.Finished)
        assertEquals(ActionOutcome.DONE, logged.single().outcome)
    }

    @Test
    fun `in-app display runs without confirmation under default policy but waits for continue`() {
        val r = runner(text, url)
        assertEquals(State.Showing(0, text), r.state)
        r.continueAfterShowing()
        assertEquals(State.Confirm(1, url), r.state)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun `every-action policy confirms in-app actions too`() {
        val r = runner(text, policy = ConfirmationPolicy.EVERY_ACTION)
        assertEquals(State.Confirm(0, text), r.state)
        r.confirm()
        assertEquals(State.Showing(0, text), r.state)
    }

    @Test
    fun `skip and cancel are logged and stop execution`() {
        val r = runner(url, TaskAction.OpenSettings(SettingsPanel.NFC), url)
        r.skip()
        assertEquals(ActionOutcome.SKIPPED, logged.last().outcome)
        r.cancel()
        assertEquals(ActionOutcome.CANCELLED, logged.last().outcome)
        assertTrue(r.state is State.Finished)
        assertTrue(performed.isEmpty())
        r.confirm()
        assertTrue(performed.isEmpty())
    }

    @Test
    fun `failures offer retry and skip logs the failure`() {
        failWith = FailureReason.NO_APP
        val r = runner(url)
        r.confirm()
        assertEquals(State.Failed(0, url, FailureReason.NO_APP), r.state)
        failWith = null
        r.retry()
        assertTrue(r.state is State.Finished)
        assertEquals(2, performed.size)

        failWith = FailureReason.NO_APP
        val r2 = runner(url)
        r2.confirm(); r2.skip()
        assertEquals(ActionOutcome.FAILED, logged.last().outcome)
        assertEquals(FailureReason.NO_APP, logged.last().reason)
    }

    @Test
    fun `disabled or invalid profiles do nothing`() {
        assertTrue(runner(url, enabled = false).state is State.Finished)
        val bad = runner(TaskAction.OpenUrl("javascript:alert(1)"))
        assertTrue(bad.state is State.Finished)
        bad.confirm()
        assertTrue(performed.isEmpty())
    }
}
