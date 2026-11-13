package com.tapandgo.root

import com.tapandgo.automation.actions.ActionParams
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootExecutionTest {
    private class FakeRunner(val rooted: Boolean, val granted: Boolean = true) : ShellRunner {
        val scripts = mutableListOf<Pair<List<String>, String?>>()
        override suspend fun run(argv: List<String>, stdin: String?, timeoutMs: Long): ShellResult {
            scripts += argv to stdin
            if (argv.first() == "su" && !rooted) return ShellResult(null, "", "", error = "not found: su")
            if (argv.first() == "su" && !granted) return ShellResult(1, "", "Permission denied")
            return when {
                argv == listOf("su", "-v") -> ShellResult(0, "27.0:MAGISK\n", "")
                stdin == "id" -> ShellResult(0, "uid=0(root) gid=0(root) context=u:r:magisk:s0\n", "")
                stdin == "getenforce" -> ShellResult(0, "Enforcing\n", "")
                stdin?.startsWith("svc wifi") == true -> ShellResult(0, "token=abc123 done", "")
                else -> ShellResult(0, "", "")
            }
        }
    }

    private fun service(r: ShellRunner) = RootExecutionService(r, null, null, sdk = 36)

    @Test
    fun `no su means not rooted and nothing executes`() = runTest {
        val r = FakeRunner(rooted = false)
        val s = service(r)
        assertEquals(RootAccess.NOT_ROOTED, s.checkAccess().access)
        val out = s.execute(RootCommand.Wifi(true))
        assertEquals("root access not granted", out.error)
        assertTrue(r.scripts.none { it.second?.startsWith("svc") == true })
        assertEquals("NO_ROOT", s.history.value.first().outcome)
    }

    @Test
    fun `denied grant is reported, not assumed`() = runTest {
        val s = service(FakeRunner(rooted = true, granted = false))
        assertEquals(RootAccess.DENIED, s.checkAccess().access)
    }

    @Test
    fun `granted access identifies Magisk and runs allowlisted script with redacted output`() = runTest {
        val r = FakeRunner(rooted = true)
        val s = service(r)
        val st = s.checkAccess()
        assertEquals(RootAccess.GRANTED, st.access)
        assertEquals(RootImplementation.MAGISK, st.implementation)
        assertEquals("Enforcing", st.selinux)
        val out = s.execute(RootCommand.Wifi(true))
        assertTrue(out.ok)
        assertEquals(listOf("su") to "svc wifi enable", r.scripts.last())
        assertFalse(out.stdout.contains("abc123"))
        assertEquals("OK", s.history.value.first().outcome)
        assertEquals("Wifi", s.history.value.first().label)
    }

    @Test
    fun `commands are built from constants and validated numbers only`() {
        assertEquals("wm density 420", RootCommand.Density(420).script())
        assertEquals("wm density reset", RootCommand.Density(null).script())
        assertEquals("wm size 1080x2400", RootCommand.Resolution(1080 to 2400).script())
        assertEquals("svc power reboot recovery", RootCommand.Reboot(RootCommand.RebootTarget.RECOVERY).script())
        assertTrue(RootCommand.Reboot(RootCommand.RebootTarget.SYSTEM).destructive)
        assertTrue(runCatching { RootCommand.Density(9999) }.isFailure)
        assertTrue(runCatching { RootCommand.Brightness(-1) }.isFailure)
        assertFalse(RootCommand.Airplane(true).supportedOnThisSdk(29))
        assertTrue(RootCommand.Airplane(true).supportedOnThisSdk(30))
        assertTrue(RootProfile.entries.all { it.steps.isNotEmpty() })
    }

    @Test
    fun `parsing and console policy`() {
        assertEquals(RootImplementation.KERNELSU, RootParsing.implementation("v1.0.1:KernelSU"))
        assertEquals(RootImplementation.APATCH, RootParsing.implementation("APatch 10763"))
        assertEquals(RootImplementation.UNKNOWN, RootParsing.implementation(""))
        assertTrue(RootParsing.isRootId("uid=0(root) gid=0(root)"))
        assertFalse(RootParsing.isRootId("uid=10234(u0_a234)"))
        assertEquals("password=•••", RootParsing.redact("password=hunter2"))
        assertFalse(CustomCommandPolicy.validate(" "))
        assertFalse(CustomCommandPolicy.validate("a".repeat(2001)))
        assertTrue(CustomCommandPolicy.validate("getprop ro.product.model"))
    }

    @Test
    fun `communication and webhook params`() {
        assertEquals("+15550100", ActionParams.phone("+1 555-0100"))
        assertNull(ActionParams.phone("call me; rm"))
        assertTrue(ActionParams.isEmail("a@example.org"))
        assertFalse(ActionParams.isEmail("not-an-email"))
        assertEquals(48.85 to 2.35, ActionParams.latLng("48.85", "2.35"))
        assertNull(ActionParams.latLng("91", "0"))
        assertNotNull(ActionParams.webhook("https://example.org/hook", "post", "{}", "json"))
        assertNull(ActionParams.webhook("http://example.org/hook", "POST", "", null))
        assertNull(ActionParams.webhook("https://user:pw@example.org/", "POST", "", null))
        assertNull(ActionParams.webhook("https://example.org/", "DELETE", "", null))
        assertNull(ActionParams.webhook("https://example.org/", "POST", "x".repeat(17 * 1024), null))
    }
}
