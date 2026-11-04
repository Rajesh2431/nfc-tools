package dev.nfcstudio.app.domain

import dev.nfcstudio.app.core.ndef.NdefParser
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.toRawRecord
import dev.nfcstudio.app.domain.task.ActionProblem
import dev.nfcstudio.app.domain.task.ProfileProblem
import dev.nfcstudio.app.domain.task.SettingsPanel
import dev.nfcstudio.app.domain.task.TaskAction
import dev.nfcstudio.app.domain.task.TaskJson
import dev.nfcstudio.app.domain.task.TaskProfile
import dev.nfcstudio.app.domain.task.TaskTrigger
import dev.nfcstudio.app.domain.task.TaskValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskValidatorTest {

    @Test
    fun `urls must be http or https and pass the uri policy`() {
        assertNull(TaskValidator.validate(TaskAction.OpenUrl("https://example.com")))
        listOf("javascript:x", "intent://x#Intent;end", "tel:+1", "file:///sdcard", "https://exa mple.com").forEach {
            assertEquals(it, ActionProblem.URL_INVALID, TaskValidator.validate(TaskAction.OpenUrl(it)))
        }
    }

    @Test
    fun `parameter validation`() {
        assertEquals(ActionProblem.EMAIL_INVALID, TaskValidator.validate(TaskAction.DraftEmail("nope")))
        assertNull(TaskValidator.validate(TaskAction.DraftEmail("a@b.example", "s", "b")))
        assertEquals(ActionProblem.PHONE_INVALID, TaskValidator.validate(TaskAction.DraftSms("12; rm -rf")))
        assertNull(TaskValidator.validate(TaskAction.DraftSms("+49 30 1234")))
        assertEquals(ActionProblem.PACKAGE_INVALID, TaskValidator.validate(TaskAction.OpenApp("com")))
        assertNull(TaskValidator.validate(TaskAction.OpenApp("com.example.app")))
        assertEquals(ActionProblem.TEXT_EMPTY, TaskValidator.validate(TaskAction.DisplayText(" ")))
        assertEquals(ActionProblem.TEXT_TOO_LONG, TaskValidator.validate(TaskAction.DisplayText("x".repeat(2001))))
        assertEquals(ActionProblem.NAME_EMPTY, TaskValidator.validate(TaskAction.ShowContact("")))
        assertNull(TaskValidator.validate(TaskAction.OpenSettings(SettingsPanel.WIFI)))
    }

    @Test
    fun `profile validation`() {
        val ok = TaskProfile(name = "n", actions = listOf(TaskAction.DisplayText("x")))
        assertNull(TaskValidator.validate(ok))
        assertEquals(ProfileProblem.NAME_EMPTY, TaskValidator.validate(ok.copy(name = "")))
        assertEquals(ProfileProblem.NO_ACTIONS, TaskValidator.validate(ok.copy(actions = emptyList())))
        assertEquals(ProfileProblem.TOO_MANY_ACTIONS, TaskValidator.validate(ok.copy(actions = List(21) { TaskAction.DisplayText("x") })))
        assertEquals(ProfileProblem.TRIGGER_INVALID, TaskValidator.validate(ok.copy(trigger = TaskTrigger.UriHost("not a host"))))
    }

    @Test
    fun `triggers only suggest enabled profiles and match hosts safely`() {
        val records = NdefParser.decodeAll(listOf(RecordSpec.Uri("https://shop.example.com/x").toRawRecord()))
        val p = TaskProfile(name = "n", actions = listOf(TaskAction.DisplayText("x")), trigger = TaskTrigger.UriHost("example.com"))
        assertTrue(TaskValidator.matches(p, records))
        assertFalse(TaskValidator.matches(p.copy(enabled = false), records))
        assertFalse(TaskValidator.matches(p.copy(trigger = TaskTrigger.UriHost("ample.com")), records))
        val spoof = NdefParser.decodeAll(listOf(RecordSpec.Uri("https://example.com@evil.test/").toRawRecord()))
        assertFalse(TaskValidator.matches(p, spoof))
        assertTrue(TaskValidator.matches(p.copy(trigger = TaskTrigger.TextContains("SHOP")), records))
        assertFalse(TaskValidator.matches(p.copy(trigger = null), records))
    }

    @Test
    fun `task tag trigger matches only its exact token`() {
        val token = "123e4567-e89b-12d3-a456-426614174000"
        val p = TaskProfile(name = "n", actions = listOf(TaskAction.DisplayText("x")), trigger = TaskTrigger.TaskTag(token))
        val tag = NdefParser.decodeAll(listOf(dev.nfcstudio.app.core.ndef.RichRecords.taskTag(token)!!.toRawRecord()))
        val other = NdefParser.decodeAll(listOf(dev.nfcstudio.app.core.ndef.RichRecords.taskTag("00000000-0000-0000-0000-000000000000")!!.toRawRecord()))
        assertTrue(TaskValidator.matches(p, tag))
        assertFalse(TaskValidator.matches(p, other))
        assertEquals(ProfileProblem.TRIGGER_INVALID, TaskValidator.validate(p.copy(trigger = TaskTrigger.TaskTag("x"))))
    }

    @Test
    fun `action json is strict data`() {
        val actions = listOf(TaskAction.OpenUrl("https://a.example"), TaskAction.OpenSettings(SettingsPanel.NFC))
        assertEquals(actions, TaskJson.decodeActions(TaskJson.encodeActions(actions)))
        assertNull(TaskJson.decodeActions("""[{"type":"run_shell","cmd":"id"}]"""))
        assertNull(TaskJson.decodeActions("""[{"type":"open_url","url":"https://a.example","extra":1}]"""))
    }
}
