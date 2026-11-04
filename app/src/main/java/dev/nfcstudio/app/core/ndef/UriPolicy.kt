package dev.nfcstudio.app.core.ndef

/**
 * Allowlist URI validation (spec 4.4 / § 10). Used before writing a URI, before
 * opening any URI read from a tag and before running a task action.
 */
object UriPolicy {

    val allowedSchemes: Set<String> = setOf("http", "https", "mailto", "tel", "sms", "geo")

    enum class Problem { EMPTY, WHITESPACE, SCHEME, HOST, CONTROL_CHARS }

    /** Facts shown to the user before opening a link. */
    data class Info(
        val scheme: String,
        val host: String?,
        /** True when the authority contains user-info ("a.com@b.com"), a classic deception. */
        val hasUserInfo: Boolean,
        /** True when the host contains non-ASCII characters (possible homograph). */
        val hasNonAsciiHost: Boolean,
        val isPlainHttp: Boolean,
    )

    /** @return null when acceptable, else the problem. */
    fun validate(uri: String): Problem? {
        if (uri.isEmpty() || uri.isBlank()) return Problem.EMPTY
        if (uri.any { it.isWhitespace() }) return Problem.WHITESPACE
        if (uri.any { it.isISOControl() || it in '​'..'‏' || it in '‪'..'‮' }) {
            return Problem.CONTROL_CHARS
        }
        val scheme = schemeOf(uri) ?: return Problem.SCHEME
        if (scheme !in allowedSchemes) return Problem.SCHEME
        if (scheme == "http" || scheme == "https") {
            if (hostOf(uri).isNullOrEmpty()) return Problem.HOST
        } else if (uri.length <= scheme.length + 1) {
            return Problem.HOST
        }
        return null
    }

    /** Facts for a URI that already passed [validate]; null otherwise. */
    fun inspect(uri: String): Info? {
        if (validate(uri) != null) return null
        val scheme = schemeOf(uri)!!
        val authority = authorityOf(uri)
        val host = hostOf(uri)
        return Info(
            scheme = scheme,
            host = host,
            hasUserInfo = authority?.contains('@') == true,
            hasNonAsciiHost = host?.any { it.code > 0x7F } == true,
            isPlainHttp = scheme == "http",
        )
    }

    private fun schemeOf(uri: String): String? {
        val idx = uri.indexOf(':')
        if (idx <= 0) return null
        val s = uri.substring(0, idx)
        if (!s[0].isLetter() || !s.all { it.isLetterOrDigit() || it in "+-." }) return null
        return s.lowercase()
    }

    private fun authorityOf(uri: String): String? {
        val start = uri.indexOf("://")
        if (start < 0) return null
        val rest = uri.substring(start + 3)
        val end = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        return rest.substring(0, end)
    }

    /** Host without user-info or port; IDN hosts are kept as typed. */
    fun hostOf(uri: String): String? {
        val authority = authorityOf(uri) ?: return null
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringBefore(']') + "]"
        } else {
            hostPort.substringBefore(':')
        }
        if (host.isEmpty()) return null
        val valid = host.startsWith("[") || host.all { it.isLetterOrDigit() || it == '-' || it == '.' }
        return if (valid) host.lowercase() else null
    }
}
