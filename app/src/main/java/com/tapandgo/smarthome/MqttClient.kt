package com.tapandgo.smarthome

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/** MQTT 3.1.1 packet encoding (publish-only client); pure for tests. */
object MqttPackets {
    fun remainingLength(n: Int): ByteArray {
        require(n in 0..268_435_455)
        val out = ByteArrayOutputStream()
        var x = n
        do {
            var b = x % 128
            x /= 128
            if (x > 0) b = b or 0x80
            out.write(b)
        } while (x > 0)
        return out.toByteArray()
    }

    private fun str(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        require(b.size <= 65_535)
        return byteArrayOf((b.size ushr 8).toByte(), b.size.toByte()) + b
    }

    private fun packet(type: Int, body: ByteArray) = byteArrayOf(type.toByte()) + remainingLength(body.size) + body

    fun connect(clientId: String, username: String?, password: String?, keepAliveSec: Int = 30): ByteArray {
        var flags = 0x02 // clean session
        if (!username.isNullOrEmpty()) flags = flags or 0x80
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) flags = flags or 0x40
        val vh = str("MQTT") + byteArrayOf(4, flags.toByte(), (keepAliveSec ushr 8).toByte(), keepAliveSec.toByte())
        var payload = str(clientId)
        if (!username.isNullOrEmpty()) payload += str(username)
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) payload += str(password)
        return packet(0x10, vh + payload)
    }

    fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean, packetId: Int): ByteArray {
        require(qos in 0..1)
        val header = 0x30 or (qos shl 1) or (if (retain) 1 else 0)
        val pid = if (qos > 0) byteArrayOf((packetId ushr 8).toByte(), packetId.toByte()) else ByteArray(0)
        return packet(header, str(topic) + pid + payload)
    }

    val DISCONNECT = byteArrayOf(0xE0.toByte(), 0)

    /** Publish topics: non-empty, no wildcards, no NUL. */
    fun isValidTopic(t: String) = t.isNotEmpty() && t.toByteArray().size <= 65_535 && '+' !in t && '#' !in t && '\u0000' !in t

    fun connackError(rc: Int): String? = when (rc) {
        0 -> null
        1 -> "broker refused: protocol version"
        2 -> "broker refused: client id"
        3 -> "broker unavailable"
        4 -> "bad username or password"
        5 -> "not authorized"
        else -> "broker refused ($rc)"
    }
}

/** Socket seam so the protocol flow is testable. */
fun interface MqttSocketFactory {
    fun open(host: String, port: Int, tls: Boolean): Socket
}

class DefaultMqttSocketFactory : MqttSocketFactory {
    override fun open(host: String, port: Int, tls: Boolean): Socket {
        val s = if (tls) SSLSocketFactory.getDefault().createSocket() else Socket()
        s.connect(InetSocketAddress(host, port), 8_000)
        s.soTimeout = 8_000
        if (s is javax.net.ssl.SSLSocket) s.startHandshake()
        return s
    }
}

/**
 * Connects, publishes one message (QoS 0 or 1 with PUBACK), disconnects.
 * Plain TCP is allowed only to local-network hosts ([LanPolicy]).
 */
class MqttPublisher(
    private val host: String,
    private val port: Int,
    private val tls: Boolean,
    private val username: String?,
    private val password: String?,
    private val clientId: String,
    private val sockets: MqttSocketFactory = DefaultMqttSocketFactory(),
) {
    suspend fun publish(topic: String, payload: String, qos: Int, retain: Boolean): SmartResult<Unit> = withContext(Dispatchers.IO) {
        if (!MqttPackets.isValidTopic(topic)) return@withContext SmartResult.Err("invalid topic (no + or # when publishing)")
        if (qos !in 0..1) return@withContext SmartResult.Err("QoS must be 0 or 1")
        if (payload.toByteArray().size > 256 * 1024) return@withContext SmartResult.Err("payload over 256 KB")
        if (!tls && !LanPolicy.isLocalHost(host)) return@withContext SmartResult.Err("unencrypted MQTT is only allowed on the local network; enable TLS")
        try {
            sockets.open(host, port, tls).use { s ->
                val out = s.getOutputStream()
                val input = DataInputStream(s.getInputStream())
                out.write(MqttPackets.connect(clientId, username, password)); out.flush()
                val type = input.readUnsignedByte()
                val len = input.readUnsignedByte()
                if (type != 0x20 || len != 2) return@withContext SmartResult.Err("not an MQTT broker")
                input.readUnsignedByte()
                MqttPackets.connackError(input.readUnsignedByte())?.let { return@withContext SmartResult.Err(it) }
                val pid = 1
                out.write(MqttPackets.publish(topic, payload.toByteArray(), qos, retain, pid)); out.flush()
                if (qos == 1) {
                    val t = input.readUnsignedByte(); val l = input.readUnsignedByte()
                    val a = input.readUnsignedByte(); val b = input.readUnsignedByte()
                    if (t != 0x40 || l != 2 || ((a shl 8) or b) != pid) return@withContext SmartResult.Err("no PUBACK from broker")
                }
                out.write(MqttPackets.DISCONNECT); out.flush()
                SmartResult.Ok(Unit)
            }
        } catch (e: java.net.SocketTimeoutException) {
            SmartResult.Err("broker timed out")
        } catch (e: IOException) {
            SmartResult.Err("connection failed: ${e.javaClass.simpleName}")
        }
    }
}
