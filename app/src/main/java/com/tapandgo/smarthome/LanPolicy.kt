package com.tapandgo.smarthome


import java.net.URI

/**
 * Plain HTTP (no TLS) is allowed only to devices on the local network:
 * RFC 1918 / link-local / loopback / CGNAT IPv4, IPv6 ULA and link-local,
 * and *.local / *.lan / *.home.arpa names. Everything else must use HTTPS.
 * Decided from the literal host text, without DNS, so a public name can never
 * be treated as local.
 */
object LanPolicy {
    private val LOCAL_SUFFIX = listOf(".local", ".lan", ".home.arpa", ".internal")

    fun isLocalHost(hostRaw: String): Boolean {
        val host = hostRaw.trim().trimStart('[').trimEnd(']').lowercase()
        if (host.isEmpty()) return false
        if (LOCAL_SUFFIX.any { host.endsWith(it) } || host == "localhost") return true
        val v4 = host.split('.').takeIf { it.size == 4 }?.map { it.toIntOrNull() ?: return false }
        if (v4 != null) {
            if (v4.any { it !in 0..255 }) return false
            val (a, b) = v4[0] to v4[1]
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        if (':' in host && host.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' }) {
            return host == "::1" || host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") ||
                host.startsWith("feb") || host.startsWith("fc") || host.startsWith("fd")
        }
        return false
    }

    /** @return a problem description, or null when [url] may be requested. */
    fun check(url: String): String? {
        val u = runCatching { URI(url) }.getOrNull() ?: return "invalid URL"
        val host = u.host ?: return "URL has no host"
        if (u.userInfo != null) return "credentials in URL are not allowed"
        return when (u.scheme?.lowercase()) {
            "https" -> null
            "http" -> if (isLocalHost(host)) null else "plain http is only allowed on the local network; use https"
            else -> "only http(s) URLs"
        }
    }

}
