package com.tapandgo.automation

import com.tapandgo.data.database.ContextTriggerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextRulesTest {
    private fun t(id: Long, type: ContextType, ssid: String? = null) = ContextTriggerEntity(id, workflowId = id * 10, type = type.name, ssid = ssid, createdAt = 0)

    @Test
    fun `ssid normalisation`() {
        assertEquals("Home", ContextRules.normalizeSsid("\"Home\""))
        assertNull(ContextRules.normalizeSsid("<unknown ssid>"))
        assertNull(ContextRules.normalizeSsid("  "))
    }

    @Test
    fun `wifi transitions fire connect and disconnect`() {
        val triggers = listOf(t(1, ContextType.WIFI_CONNECT, "Home"), t(2, ContextType.WIFI_DISCONNECT, "Home"), t(3, ContextType.WIFI_CONNECT, "Office"))
        assertEquals(listOf(1L), ContextRules.wifiEvents(null, "Home", triggers).map { it.id })
        assertEquals(listOf(2L), ContextRules.wifiEvents("Home", null, triggers).map { it.id })
        assertEquals(listOf(2L, 3L), ContextRules.wifiEvents("Home", "Office", triggers).map { it.id })
        assertTrue(ContextRules.wifiEvents("Home", "Home", triggers).isEmpty())
    }

    @Test
    fun `area events and validation`() {
        assertTrue(ContextRules.areaEvent(t(1, ContextType.AREA_ENTER), entering = true))
        assertFalse(ContextRules.areaEvent(t(1, ContextType.AREA_ENTER), entering = false))
        assertTrue(ContextRules.areaEvent(t(1, ContextType.AREA_EXIT), entering = false))
        assertNull(ContextRules.validate(ContextType.AREA_ENTER, null, 48.85, 2.35, 200))
        assertEquals(ContextRules.Problem.RADIUS, ContextRules.validate(ContextType.AREA_EXIT, null, 48.85, 2.35, 10))
        assertEquals(ContextRules.Problem.LOCATION, ContextRules.validate(ContextType.AREA_EXIT, null, 95.0, 2.35, 200))
        assertEquals(ContextRules.Problem.SSID, ContextRules.validate(ContextType.WIFI_CONNECT, "x".repeat(33), null, null, null))
        assertNull(ContextRules.validate(ContextType.WIFI_CONNECT, "Home", null, null, null))
    }
}
