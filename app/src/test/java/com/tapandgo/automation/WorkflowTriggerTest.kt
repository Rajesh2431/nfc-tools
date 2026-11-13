package com.tapandgo.automation

import com.tapandgo.automation.engine.Block
import com.tapandgo.automation.engine.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowTriggerTest {
    private val token = "0f8fad5b-d9cb-469f-a165-70867728950e"

    @Test
    fun `internal alias accepts only shortcut and tile sources`() {
        val p = { src: String? -> WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, WorkflowLaunch.INTERNAL_ALIAS, token, src) }
        assertEquals(LaunchRequest(token, RunSource.SHORTCUT), p("SHORTCUT"))
        assertEquals(LaunchRequest(token, RunSource.TILE), p("TILE"))
        assertNull(p("SCHEDULE"))
        assertNull(p("MANUAL"))
        assertNull(p(null))
        assertNull(p("bogus"))
    }

    @Test
    fun `external alias is always EXTERNAL whatever source the caller claims`() {
        assertEquals(
            LaunchRequest(token, RunSource.EXTERNAL),
            WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, WorkflowLaunch.EXTERNAL_ALIAS, token, "SHORTCUT"),
        )
    }

    @Test
    fun `wrong action, component or malformed token is rejected`() {
        assertNull(WorkflowLaunch.parse("android.intent.action.MAIN", WorkflowLaunch.EXTERNAL_ALIAS, token, null))
        assertNull(WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, "com.tapandgo.MainActivity", token, "SHORTCUT"))
        assertNull(WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, WorkflowLaunch.EXTERNAL_ALIAS, "rm -rf /", null))
        assertNull(WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, WorkflowLaunch.EXTERNAL_ALIAS, null, null))
    }

    @Test
    fun `launch policy`() {
        val off = WorkflowTriggers(token)
        val auto = off.copy(autoRun = true)
        val ext = off.copy(allowExternal = true)
        assertEquals(LaunchDecision.CONFIRM, LaunchPolicy.decide(RunSource.NFC_TAG, off, externalEnabled = true))
        assertEquals(LaunchDecision.RUN, LaunchPolicy.decide(RunSource.NFC_TAG, auto, externalEnabled = false))
        assertEquals(LaunchDecision.RUN, LaunchPolicy.decide(RunSource.SHORTCUT, off, externalEnabled = false))
        assertEquals(LaunchDecision.RUN, LaunchPolicy.decide(RunSource.TILE, off, externalEnabled = false))
        // External needs both the global switch and the per-workflow opt-in.
        assertEquals(LaunchDecision.BLOCKED, LaunchPolicy.decide(RunSource.EXTERNAL, auto, externalEnabled = true))
        assertEquals(LaunchDecision.BLOCKED, LaunchPolicy.decide(RunSource.EXTERNAL, ext, externalEnabled = false))
        assertEquals(LaunchDecision.CONFIRM, LaunchPolicy.decide(RunSource.EXTERNAL, ext, externalEnabled = true))
        assertEquals(LaunchDecision.RUN, LaunchPolicy.decide(RunSource.EXTERNAL, ext.copy(autoRun = true), externalEnabled = true))
    }

    @Test
    fun `schedule choices respect the WorkManager minimum`() {
        assertTrue(WorkflowTriggers.SCHEDULE_CHOICES.filter { it != 0 }.all { it >= 15 })
        assertTrue(WorkflowTriggers.isValidSchedule(0))
        assertEquals(false, WorkflowTriggers.isValidSchedule(5))
    }

    @Test
    fun `transfer round trip and untrusted input`() {
        val allowed = setOf("flashlight")
        val good = WorkflowDto("d", Workflow("Torch", listOf(Block.Action("flashlight", mapOf("state" to "on")))))
        val bad = WorkflowDto("", Workflow("Bad", listOf(Block.Action("shell.exec"))))
        val parsed = WorkflowTransfer.decode(WorkflowTransfer.encode(listOf(good, bad)), allowed)
        assertNotNull(parsed)
        assertEquals(listOf(good), parsed!!.valid)
        assertEquals(1, parsed.skipped)
        assertNull(WorkflowTransfer.decode("{}", allowed))
        assertNull(WorkflowTransfer.decode("""{"format":"other","version":1,"workflows":[]}""", allowed))
        assertNull(WorkflowTransfer.decode("not json", allowed))
        // Exports never carry trigger tokens.
        assertTrue("triggerToken" !in WorkflowTransfer.encode(listOf(good)))
    }
}
