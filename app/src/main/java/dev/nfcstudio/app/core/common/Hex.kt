package dev.nfcstudio.app.core.common

/** Lowercase hex without separators. */
fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
    }
    return out.toString()
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/** Groups hex into "AA BB CC" pairs for display. */
fun String.groupHexPairs(): String = chunked(2).joinToString(" ") { it.uppercase() }

/**
 * Parses user/stored hex. Whitespace and ':' separators are ignored.
 * @return null when the input contains non-hex characters or an odd digit count.
 */
fun String.hexToBytesOrNull(): ByteArray? {
    val clean = filterNot { it.isWhitespace() || it == ':' }
    if (clean.length % 2 != 0) return null
    val out = ByteArray(clean.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(clean[2 * i], 16)
        val lo = Character.digit(clean[2 * i + 1], 16)
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

/**
 * Printable-ASCII rendering of bytes: printable characters are kept, every
 * other byte becomes '.', so control characters and deceptive Unicode from
 * untrusted tags are never rendered.
 */
fun ByteArray.toAsciiPreview(): String = buildString(size) {
    for (b in this@toAsciiPreview) {
        val c = b.toInt() and 0xFF
        append(if (c in 0x20..0x7E) c.toChar() else '.')
    }
}
