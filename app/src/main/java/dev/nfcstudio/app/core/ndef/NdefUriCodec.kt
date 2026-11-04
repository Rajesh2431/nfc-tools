package dev.nfcstudio.app.core.ndef

/**
 * NFC Forum URI record codec (URI RTD 1.0, table 3) — identical to
 * android.nfc.NdefRecord's URI_PREFIX_MAP. Payload: 1 prefix byte followed by
 * the remainder encoded as UTF-8.
 */
object NdefUriCodec {

    /** Abbreviation table, codes 0x00..0x23. Order is normative. */
    val prefixes: List<String> = listOf(
        "", // 0x00
        "http://www.", // 0x01
        "https://www.", // 0x02
        "http://", // 0x03
        "https://", // 0x04
        "tel:", // 0x05
        "mailto:", // 0x06
        "ftp://anonymous:anonymous@", // 0x07
        "ftp://ftp.", // 0x08
        "ftps://", // 0x09
        "sftp://", // 0x0A
        "smb://", // 0x0B
        "nfs://", // 0x0C
        "ftp://", // 0x0D
        "dav://", // 0x0E
        "news:", // 0x0F
        "telnet://", // 0x10
        "imap:", // 0x11
        "rtsp://", // 0x12
        "urn:", // 0x13
        "pop:", // 0x14
        "sip:", // 0x15
        "sips:", // 0x16
        "tftp:", // 0x17
        "btspp://", // 0x18
        "btl2cap://", // 0x19
        "btgoep://", // 0x1A
        "tcpobex://", // 0x1B
        "irdaobex://", // 0x1C
        "file://", // 0x1D
        "urn:epc:id:", // 0x1E
        "urn:epc:tag:", // 0x1F
        "urn:epc:pat:", // 0x20
        "urn:epc:raw:", // 0x21
        "urn:epc:", // 0x22
        "urn:nfc:", // 0x23
    )

    fun encode(uri: String): ByteArray {
        require(uri.isNotEmpty()) { "uri must not be empty" }
        var bestIndex = 0
        var bestLength = 0
        prefixes.forEachIndexed { index, prefix ->
            if (prefix.length > bestLength && uri.startsWith(prefix)) {
                bestIndex = index
                bestLength = prefix.length
            }
        }
        return byteArrayOf(bestIndex.toByte()) + uri.substring(bestLength).toByteArray(Charsets.UTF_8)
    }

    fun decode(payload: ByteArray): String {
        if (payload.isEmpty()) throw NdefParseException("empty uri payload")
        val index = payload[0].toInt() and 0xFF
        if (index >= prefixes.size) throw NdefParseException("reserved uri prefix code $index")
        return prefixes[index] + payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
    }
}
