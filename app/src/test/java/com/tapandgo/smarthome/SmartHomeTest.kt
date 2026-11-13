package com.tapandgo.smarthome

import com.tapandgo.core.common.toHex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class SmartHomeTest {
    private class FakeHttp(val reply: (String, String, String?) -> SmartResult<HttpResponse>) : HttpTransport {
        val calls = mutableListOf<Triple<String, String, String?>>()
        var lastHeaders: Map<String, String> = emptyMap()
        override suspend fun request(url: String, method: String, headers: Map<String, String>, body: String?): SmartResult<HttpResponse> {
            calls += Triple(url, method, body); lastHeaders = headers
            return reply(url, method, body)
        }
    }

    @Test
    fun `lan policy allows plain http only to local addresses`() {
        listOf("192.168.1.5", "10.0.0.2", "172.20.1.1", "127.0.0.1", "169.254.3.4", "100.64.0.1", "homeassistant.local", "nas.lan", "fd12::1", "[fe80::1]")
            .forEach { assertTrue(it, LanPolicy.isLocalHost(it)) }
        listOf("8.8.8.8", "172.32.0.1", "example.com", "192.168.1.5.evil.com", "local", "999.1.1.1", "")
            .forEach { assertFalse(it, LanPolicy.isLocalHost(it)) }
        assertNull(LanPolicy.check("http://192.168.1.5:8123/api/"))
        assertNull(LanPolicy.check("https://ha.example.com/api/"))
        assertTrue(LanPolicy.check("http://ha.example.com/api/")!!.startsWith("plain http"))
        assertTrue(LanPolicy.check("http://user:pw@192.168.1.5/") != null)
        assertTrue(LanPolicy.check("ftp://192.168.1.5/") != null)
    }

    @Test
    fun `connection validation`() {
        val ha = ConnectionDraft(kind = SmartKind.HOME_ASSISTANT, name = "Home", address = "http://192.168.1.10:8123", secret = "tok")
        assertNull(ConnectionValidator.validate(ha))
        assertEquals(ConnectionProblem.SECRET_REQUIRED, ConnectionValidator.validate(ha.copy(secret = "")))
        assertNull(ConnectionValidator.validate(ha.copy(secret = null), hasStoredSecret = true))
        assertEquals(ConnectionProblem.PLAIN_REMOTE, ConnectionValidator.validate(ha.copy(address = "http://ha.example.com")))
        assertEquals(ConnectionProblem.NAME, ConnectionValidator.validate(ha.copy(name = "a;b")))
        val mqtt = ConnectionDraft(kind = SmartKind.MQTT, name = "Broker", address = "broker.example.com", port = 1883)
        assertEquals(ConnectionProblem.PLAIN_REMOTE, ConnectionValidator.validate(mqtt))
        assertNull(ConnectionValidator.validate(mqtt.copy(useTls = true, port = 8883)))
        assertEquals(ConnectionProblem.PORT, ConnectionValidator.validate(mqtt.copy(useTls = true, port = 0)))
        assertNull(ConnectionValidator.validate(ConnectionDraft(kind = SmartKind.HUE, name = "Hue", address = "192.168.1.30", port = 80, secret = "")))
    }

    @Test
    fun `home assistant service call body and auth`() = runTest {
        val http = FakeHttp { _, _, _ -> SmartResult.Ok(HttpResponse(200, "[]")) }
        val c = HomeAssistantClient("http://192.168.1.10:8123/", "secret-token", http)
        assertTrue(c.callService("light.turn_on", "light.kitchen", """{"brightness_pct":40}""") is SmartResult.Ok)
        val (url, method, body) = http.calls.single()
        assertEquals("http://192.168.1.10:8123/api/services/light/turn_on", url)
        assertEquals("POST", method)
        assertTrue(body!!.contains("\"entity_id\":\"light.kitchen\"") && body.contains("\"brightness_pct\":40"))
        assertEquals("Bearer secret-token", http.lastHeaders["Authorization"])
        assertTrue(c.callService("light/../x", null, null) is SmartResult.Err)
        assertTrue(c.callService("light.turn_on", "bad entity", null) is SmartResult.Err)
        assertTrue(c.callService("light.turn_on", "light.a", "[1]") is SmartResult.Err)
    }

    @Test
    fun `home assistant parses states and maps 401`() = runTest {
        val states = """[{"entity_id":"switch.fan","state":"on","attributes":{"friendly_name":"Fan"}},{"entity_id":"sensor.t","state":"21.5","attributes":{}}]"""
        val ok = HomeAssistantClient.parseStates(states) as SmartResult.Ok
        assertEquals(listOf("sensor.t", "switch.fan"), ok.value.map { it.entityId })
        assertTrue(ok.value[1].toggleable); assertFalse(ok.value[0].toggleable)
        val c = HomeAssistantClient("http://192.168.1.10:8123", "t", FakeHttp { _, _, _ -> SmartResult.Ok(HttpResponse(401, "")) })
        assertEquals("token rejected (401)", (c.check() as SmartResult.Err).reason)
    }

    @Test
    fun `hue pairing and errors`() = runTest {
        assertEquals("link button not pressed", (HueClient.parsePair("""[{"error":{"type":101,"address":"","description":"link button not pressed"}}]""") as SmartResult.Err).reason)
        assertEquals("abc123", (HueClient.parsePair("""[{"success":{"username":"abc123"}}]""") as SmartResult.Ok).value)
        val http = FakeHttp { _, _, _ -> SmartResult.Ok(HttpResponse(200, """[{"success":{}}]""")) }
        val c = HueClient("192.168.1.30", "key", http)
        assertTrue(c.setLight("3", true, 50) is SmartResult.Ok)
        assertEquals(Triple("http://192.168.1.30/api/key/lights/3/state", "PUT", """{"on":true,"bri":127}"""), http.calls.single())
        assertTrue(c.setLight("../1", true, null) is SmartResult.Err)
    }

    @Test
    fun `http device requests`() {
        assertEquals("/relay/0?turn=on", HttpDeviceRequests.switch(HttpDeviceType.SHELLY_GEN1, true)!!.path)
        assertEquals("/rpc/Switch.Toggle?id=1", HttpDeviceRequests.switch(HttpDeviceType.SHELLY_GEN2, null, 1)!!.path)
        assertEquals("/cm?cmnd=Power1%20Off", HttpDeviceRequests.switch(HttpDeviceType.TASMOTA, false)!!.path)
        assertEquals("/cm?cmnd=Dimmer%2040", HttpDeviceRequests.switch(HttpDeviceType.TASMOTA, true, brightnessPercent = 40)!!.path)
        val w = HttpDeviceRequests.switch(HttpDeviceType.WLED, true, brightnessPercent = 100)!!
        assertEquals("POST" to """{"on":true,"bri":255}""", w.method to w.body)
        assertNull(HttpDeviceRequests.switch(HttpDeviceType.SHELLY_GEN1, true, 9))
    }

    @Test
    fun `mqtt packets`() {
        assertEquals("00", MqttPackets.remainingLength(0).toHex().lowercase())
        assertEquals("7f", MqttPackets.remainingLength(127).toHex().lowercase())
        assertEquals("8001", MqttPackets.remainingLength(128).toHex().lowercase())
        assertEquals("ffff7f", MqttPackets.remainingLength(2_097_151).toHex().lowercase())
        val c = MqttPackets.connect("id", null, null).toHex().lowercase()
        assertEquals("100e00044d5154540402001e00026964", c)
        assertEquals("3008000374657368690a", MqttPackets.publish("tes", "hi\n".toByteArray(), 0, false, 1).toHex().lowercase())
        assertEquals("33", MqttPackets.publish("t", byteArrayOf(), 1, true, 7).toHex().lowercase().take(2))
        assertFalse(MqttPackets.isValidTopic("home/+/x")); assertFalse(MqttPackets.isValidTopic("")); assertTrue(MqttPackets.isValidTopic("home/door"))
        assertEquals("bad username or password", MqttPackets.connackError(4))
    }

    @Test
    fun `mqtt publish flow with qos1`() = runTest {
        val sent = ByteArrayOutputStream()
        val socket = object : Socket() {
            override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf(0x20, 2, 0, 0, 0x40, 2, 0, 1))
            override fun getOutputStream(): OutputStream = sent
        }
        val p = MqttPublisher("192.168.1.5", 1883, false, "u", "p", "tapandgo-1") { _, _, _ -> socket }
        assertTrue(p.publish("home/door", "open", 1, false) is SmartResult.Ok)
        assertTrue(sent.toByteArray().toHex().lowercase().endsWith("e000"))
        val remote = MqttPublisher("broker.example.com", 1883, false, null, null, "x") { _, _, _ -> socket }
        assertTrue((remote.publish("a", "b", 0, false) as SmartResult.Err).reason.contains("local network"))
        val refused = MqttPublisher("192.168.1.5", 1883, false, "u", "bad", "x") { _, _, _ ->
            object : Socket() {
                override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf(0x20, 2, 0, 4))
                override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
            }
        }
        assertEquals("bad username or password", (refused.publish("a", "b", 0, false) as SmartResult.Err).reason)
    }
}
