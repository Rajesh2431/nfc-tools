package com.tapandgo.data

import com.tapandgo.automation.WorkflowDto
import com.tapandgo.automation.engine.Block
import com.tapandgo.automation.engine.Workflow
import com.tapandgo.data.export.ExportCodec
import com.tapandgo.data.export.ImportResult
import com.tapandgo.data.export.SmartConnectionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWorkflowsTest {
    @Test
    fun `workflows and connections round trip, invalid ones skipped, no secrets`() {
        val ok = WorkflowDto("d", Workflow("Torch", listOf(Block.Action("flashlight", mapOf("state" to "on")))))
        val bad = WorkflowDto("", Workflow("Bad", listOf(Block.Action("shell.exec"))))
        val conn = SmartConnectionDto("HOME_ASSISTANT", "Home", "http://192.168.1.10:8123", 8123, false)
        val remotePlain = SmartConnectionDto("MQTT", "Remote", "broker.example.com", 1883, false)
        val text = ExportCodec.encode(emptyList(), emptyList(), 1L, workflows = listOf(ok, bad), smartHome = listOf(conn, remotePlain))
        assertFalse(text.contains("secret", ignoreCase = true))
        val r = ExportCodec.decode(text, 2L, keepPayload = false, keepUid = false, allowedActions = setOf("flashlight")) as ImportResult.Ok
        assertEquals(listOf(ok), r.bundle.workflows)
        assertEquals(listOf(conn), r.bundle.smartHome)
        assertEquals(2, r.bundle.skippedOther)
    }

    @Test
    fun `old backups without the new sections still import`() {
        val text = ExportCodec.encode(emptyList(), emptyList(), 1L)
            .replace(Regex(",\\s*\"workflows\"\\s*:\\s*\\[\\s*]"), "")
            .replace(Regex(",\\s*\"smartHome\"\\s*:\\s*\\[\\s*]"), "")
        assertFalse(text.contains("workflows"))
        assertTrue(ExportCodec.decode(text, 2L, false, false) is ImportResult.Ok)
    }
}
