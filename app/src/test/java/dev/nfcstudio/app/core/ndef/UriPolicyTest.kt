package dev.nfcstudio.app.core.ndef

import dev.nfcstudio.app.core.ndef.UriPolicy.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UriPolicyTest {

    @Test
    fun `accepts http and https`() {
        assertNull(UriPolicy.validate("https://example.com/x?y=1"))
        assertNull(UriPolicy.validate("http://example.com"))
        assertNull(UriPolicy.validate("https://example.com:8443/a"))
    }

    @Test
    fun `accepts allowlisted non-web schemes`() {
        assertNull(UriPolicy.validate("mailto:a@b.example"))
        assertNull(UriPolicy.validate("tel:+12345"))
        assertNull(UriPolicy.validate("sms:+12345?body=hi"))
        assertNull(UriPolicy.validate("geo:52.5,13.4"))
    }

    @Test
    fun `rejects dangerous schemes`() {
        listOf(
            "file:///etc/passwd", "intent://x#Intent;end", "javascript:alert(1)",
            "content://settings", "data:text/html,<b>x</b>", "android-app://com.x",
        ).forEach { assertEquals(it, Problem.SCHEME, UriPolicy.validate(it)) }
    }

    @Test
    fun `rejects empty whitespace and control characters`() {
        assertEquals(Problem.EMPTY, UriPolicy.validate(""))
        assertEquals(Problem.EMPTY, UriPolicy.validate("   "))
        assertEquals(Problem.WHITESPACE, UriPolicy.validate("https://exa mple.com"))
        assertEquals(Problem.CONTROL_CHARS, UriPolicy.validate("https://example.com/‮gnp.exe"))
    }

    @Test
    fun `rejects http without host and bare schemes`() {
        assertEquals(Problem.HOST, UriPolicy.validate("https:///path"))
        assertEquals(Problem.HOST, UriPolicy.validate("https://"))
        assertEquals(Problem.HOST, UriPolicy.validate("tel:"))
    }

    @Test
    fun `scheme match is case-insensitive and missing scheme rejected`() {
        assertNull(UriPolicy.validate("HTTPS://Example.com"))
        assertEquals(Problem.SCHEME, UriPolicy.validate("example.com/page"))
    }

    @Test
    fun `inspect exposes real host behind user info`() {
        val info = UriPolicy.inspect("https://bank.example@evil.example/login")!!
        assertEquals("evil.example", info.host)
        assertTrue(info.hasUserInfo)
    }

    @Test
    fun `inspect flags non-ascii host and plain http`() {
        val info = UriPolicy.inspect("http://pаypal.example")!! // Cyrillic 'а'
        assertTrue(info.hasNonAsciiHost)
        assertTrue(info.isPlainHttp)
        assertFalse(UriPolicy.inspect("https://example.com")!!.isPlainHttp)
    }

    @Test
    fun `inspect returns null for blocked uris`() {
        assertNull(UriPolicy.inspect("javascript:alert(1)"))
    }
}
