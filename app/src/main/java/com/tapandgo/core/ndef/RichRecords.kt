package com.tapandgo.core.ndef

import com.tapandgo.core.common.toHex
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

/**
 * Builders and bounded decoders for well-known NDEF payload formats beyond
 * plain text/URI. Builders produce [RecordSpec]s; decoders never throw past
 * [NdefParseException] and never act on the data (display only).
 */
object RichRecords {

    // ---------- URI-based builders ----------

    private val PHONE = Regex("^\\+?[0-9 ()-]{3,20}$")
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,253}\\.[A-Za-z]{2,63}$")

    fun isValidPhone(s: String) = PHONE.matches(s.trim())
    fun isValidEmail(s: String) = EMAIL.matches(s.trim())

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20") // String overload: API 1 (Charset overload is API 33)
    private fun phoneDigits(s: String) = s.trim().filter { it.isDigit() || it == '+' }

    fun phone(number: String): RecordSpec? = if (isValidPhone(number)) RecordSpec.Uri("tel:" + phoneDigits(number)) else null

    fun sms(number: String, body: String): RecordSpec? {
        if (!isValidPhone(number) || body.length > 1000) return null
        return RecordSpec.Uri("sms:" + phoneDigits(number) + if (body.isEmpty()) "" else "?body=" + enc(body))
    }

    fun email(to: String, subject: String, body: String): RecordSpec? {
        if (!isValidEmail(to) || subject.length > 200 || body.length > 2000) return null
        val query = listOfNotNull(subject.takeIf { it.isNotEmpty() }?.let { "subject=" + enc(it) }, body.takeIf { it.isNotEmpty() }?.let { "body=" + enc(it) })
        return RecordSpec.Uri("mailto:" + to.trim() + if (query.isEmpty()) "" else "?" + query.joinToString("&"))
    }

    fun location(latitude: Double, longitude: Double): RecordSpec? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || latitude.isNaN() || longitude.isNaN()) return null
        return RecordSpec.Uri("geo:%.6f,%.6f".format(java.util.Locale.ROOT, latitude, longitude))
    }

    fun address(address: String): RecordSpec? {
        val a = address.trim()
        if (a.isEmpty() || a.length > 300) return null
        return RecordSpec.Uri("geo:0,0?q=" + enc(a))
    }

    // ---------- Contact (vCard 3.0, RFC 2426) ----------

    data class Contact(
        val name: String,
        val phone: String = "",
        val email: String = "",
        val organization: String = "",
        val address: String = "",
        val url: String = "",
    )

    private fun vEscape(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\r\n", "\\n").replace("\n", "\\n")

    fun contact(c: Contact): RecordSpec? {
        if (c.name.isBlank() || c.name.length > 100) return null
        if (c.phone.isNotEmpty() && !isValidPhone(c.phone)) return null
        if (c.email.isNotEmpty() && !isValidEmail(c.email)) return null
        if (c.url.isNotEmpty() && UriPolicy.validate(c.url) != null) return null
        if (listOf(c.organization, c.address).any { it.length > 200 }) return null
        val lines = buildList {
            add("BEGIN:VCARD"); add("VERSION:3.0")
            add("N:" + vEscape(c.name.trim()) + ";;;;")
            add("FN:" + vEscape(c.name.trim()))
            if (c.phone.isNotEmpty()) add("TEL:" + vEscape(c.phone.trim()))
            if (c.email.isNotEmpty()) add("EMAIL:" + vEscape(c.email.trim()))
            if (c.organization.isNotBlank()) add("ORG:" + vEscape(c.organization.trim()))
            if (c.address.isNotBlank()) add("ADR:;;" + vEscape(c.address.trim()) + ";;;;")
            if (c.url.isNotEmpty()) add("URL:" + c.url.trim())
            add("END:VCARD")
        }
        return RecordSpec.Mime("text/vcard", lines.joinToString("\r\n") + "\r\n")
    }

    /** Extracts display fields from a vCard. Bounded; unknown properties are ignored. */
    fun decodeContact(payload: ByteArray): List<Detail> {
        if (payload.size > 16 * 1024) throw NdefParseException("vCard larger than 16 KiB")
        val text = payload.toString(Charsets.UTF_8)
        if (!text.trimStart().startsWith("BEGIN:VCARD", ignoreCase = true)) throw NdefParseException("not a vCard")
        // RFC 2425 line unfolding: CRLF followed by space/tab continues the line.
        val unfolded = text.replace(Regex("\r?\n[ \t]"), "")
        val out = mutableListOf<Detail>()
        for (line in unfolded.lines().take(200)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val prop = line.substring(0, colon).substringBefore(';').uppercase()
            val value = unescapeV(line.substring(colon + 1)).take(500)
            val label = when (prop) {
                "FN" -> Detail.Label.NAME
                "TEL" -> Detail.Label.PHONE
                "EMAIL" -> Detail.Label.EMAIL
                "ORG" -> Detail.Label.ORGANIZATION
                "ADR" -> Detail.Label.ADDRESS
                "URL" -> Detail.Label.URL
                "TITLE" -> Detail.Label.TITLE
                else -> null
            } ?: continue
            val cleaned = if (label == Detail.Label.ADDRESS) value.split(';').filter { it.isNotBlank() }.joinToString(", ") else value
            if (cleaned.isNotBlank()) out += Detail(label, cleaned)
        }
        return out
    }

    private fun unescapeV(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                append(if (n == 'n' || n == 'N') '\n' else n)
                i += 2
            } else {
                append(c); i++
            }
        }
    }

    // ---------- Wi-Fi (Wi-Fi Simple Configuration credential) ----------

    enum class WifiAuth(val code: Int) { OPEN(0x0001), WPA_PERSONAL(0x0002), WPA2_PERSONAL(0x0020), WPA_WPA2_PERSONAL(0x0022) }
    enum class WifiEncryption(val code: Int) { NONE(0x0001), TKIP(0x0004), AES(0x0008), TKIP_AES(0x000C) }

    data class Wifi(val ssid: String, val auth: WifiAuth, val key: String, val encryption: WifiEncryption = if (auth == WifiAuth.OPEN) WifiEncryption.NONE else WifiEncryption.AES)

    const val WIFI_MIME = "application/vnd.wfa.wsc"
    private const val T_VERSION = 0x104A
    private const val T_CREDENTIAL = 0x100E
    private const val T_NETWORK_INDEX = 0x1026
    private const val T_SSID = 0x1045
    private const val T_AUTH = 0x1003
    private const val T_ENCRYPTION = 0x100F
    private const val T_KEY = 0x1027
    private const val T_MAC = 0x1020

    private fun ByteArrayOutputStream.tlv(type: Int, value: ByteArray) {
        write(type ushr 8); write(type and 0xFF); write(value.size ushr 8); write(value.size and 0xFF); write(value)
    }

    private fun u16(v: Int) = byteArrayOf((v ushr 8).toByte(), (v and 0xFF).toByte())

    fun wifiProblem(w: Wifi): Boolean {
        val ssidBytes = w.ssid.toByteArray(Charsets.UTF_8).size
        if (ssidBytes !in 1..32) return true
        return when (w.auth) {
            WifiAuth.OPEN -> w.key.isNotEmpty()
            else -> w.key.length !in 8..63 || (w.key.length == 64 && !w.key.all { it.isLetterOrDigit() })
        }
    }

    fun wifi(w: Wifi): RecordSpec? {
        if (wifiProblem(w)) return null
        val cred = ByteArrayOutputStream().apply {
            tlv(T_NETWORK_INDEX, byteArrayOf(1))
            tlv(T_SSID, w.ssid.toByteArray(Charsets.UTF_8))
            tlv(T_AUTH, u16(w.auth.code))
            tlv(T_ENCRYPTION, u16(w.encryption.code))
            tlv(T_KEY, w.key.toByteArray(Charsets.UTF_8))
            tlv(T_MAC, ByteArray(6) { 0xFF.toByte() })
        }.toByteArray()
        val payload = ByteArrayOutputStream().apply {
            tlv(T_VERSION, byteArrayOf(0x10))
            tlv(T_CREDENTIAL, cred)
        }.toByteArray()
        return RecordSpec.MimeBinary(WIFI_MIME, payload.toHex())
    }

    private fun tlvs(bytes: ByteArray): Map<Int, ByteArray> {
        val out = LinkedHashMap<Int, ByteArray>()
        var p = 0
        while (p + 4 <= bytes.size) {
            val type = ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF)
            val len = ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
            p += 4
            if (p + len > bytes.size) throw NdefParseException("truncated Wi-Fi TLV")
            out.putIfAbsent(type, bytes.copyOfRange(p, p + len))
            p += len
            if (out.size > 64) throw NdefParseException("too many Wi-Fi TLVs")
        }
        if (p != bytes.size) throw NdefParseException("trailing Wi-Fi bytes")
        return out
    }

    fun decodeWifi(payload: ByteArray): List<Detail> {
        val cred = tlvs(payload)[T_CREDENTIAL] ?: throw NdefParseException("no Wi-Fi credential")
        val f = tlvs(cred)
        val ssid = f[T_SSID]?.toString(Charsets.UTF_8) ?: throw NdefParseException("no SSID")
        fun code(b: ByteArray?) = b?.takeIf { it.size == 2 }?.let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
        val auth = code(f[T_AUTH])?.let { c -> WifiAuth.entries.firstOrNull { it.code == c }?.name ?: "0x%04X".format(c) }
        val encryption = code(f[T_ENCRYPTION])?.let { c -> WifiEncryption.entries.firstOrNull { it.code == c }?.name ?: "0x%04X".format(c) }
        return listOfNotNull(
            Detail(Detail.Label.SSID, ssid),
            auth?.let { Detail(Detail.Label.AUTHENTICATION, it) },
            encryption?.let { Detail(Detail.Label.ENCRYPTION, it) },
            f[T_KEY]?.takeIf { it.isNotEmpty() }?.let { Detail(Detail.Label.NETWORK_KEY, it.toString(Charsets.UTF_8), secret = true) },
        )
    }

    // ---------- Bluetooth (BR/EDR OOB, Bluetooth Secure Simple Pairing) ----------

    const val BLUETOOTH_MIME = "application/vnd.bluetooth.ep.oob"
    private val MAC = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

    fun isValidMac(mac: String) = MAC.matches(mac.trim())

    fun bluetooth(mac: String, name: String): RecordSpec? {
        if (!isValidMac(mac) || name.toByteArray(Charsets.UTF_8).size > 200) return null
        val addr = mac.trim().split(':', '-').map { it.toInt(16).toByte() }.reversed().toByteArray()
        val eir = ByteArrayOutputStream().apply {
            if (name.isNotEmpty()) {
                val n = name.toByteArray(Charsets.UTF_8)
                write(n.size + 1); write(0x09); write(n) // Complete Local Name
            }
        }.toByteArray()
        val total = 2 + 6 + eir.size
        val payload = byteArrayOf((total and 0xFF).toByte(), (total ushr 8).toByte()) + addr + eir
        return RecordSpec.MimeBinary(BLUETOOTH_MIME, payload.toHex())
    }

    fun decodeBluetooth(payload: ByteArray): List<Detail> {
        if (payload.size < 8) throw NdefParseException("Bluetooth OOB shorter than 8 bytes")
        val declared = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
        if (declared > payload.size) throw NdefParseException("Bluetooth OOB length exceeds payload")
        val mac = (7 downTo 2).joinToString(":") { "%02X".format(payload[it].toInt() and 0xFF) }
        val out = mutableListOf(Detail(Detail.Label.MAC, mac))
        var p = 8
        while (p < declared) {
            val len = payload[p].toInt() and 0xFF
            if (len == 0 || p + 1 + len > declared) break
            val type = payload[p + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) out += Detail(Detail.Label.DEVICE_NAME, payload.copyOfRange(p + 2, p + 1 + len).toString(Charsets.UTF_8))
            p += 1 + len
        }
        return out
    }

    // ---------- Android Application Record / task tag ----------

    const val AAR_TYPE = "android.com:pkg"
    const val TASK_TYPE = "tapandgo.app:task"
    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val TOKEN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun isValidPackage(p: String) = p.length <= 150 && PACKAGE.matches(p)
    fun isValidToken(t: String) = TOKEN.matches(t)

    fun appLaunch(packageName: String): RecordSpec? =
        packageName.trim().takeIf(::isValidPackage)?.let { RecordSpec.External(AAR_TYPE, it.toByteArray(Charsets.US_ASCII).toHex()) }

    /** A task tag carries only a random token; the actions stay in the app. */
    fun taskTag(token: String): RecordSpec? =
        token.takeIf(::isValidToken)?.let { RecordSpec.External(TASK_TYPE, it.toByteArray(Charsets.US_ASCII).toHex()) }
}

/** A labelled value decoded from a rich record. [secret] values are masked until revealed. */
data class Detail(val label: Label, val value: String, val secret: Boolean = false) {
    enum class Label { NAME, PHONE, EMAIL, ORGANIZATION, ADDRESS, URL, TITLE, SSID, AUTHENTICATION, ENCRYPTION, NETWORK_KEY, MAC, DEVICE_NAME, PACKAGE, TOKEN }
}
