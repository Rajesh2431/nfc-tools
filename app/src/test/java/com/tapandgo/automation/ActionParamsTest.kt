package com.tapandgo.automation

import com.tapandgo.automation.actions.ActionParams
import com.tapandgo.automation.actions.BuiltinVariables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionParamsTest {
    @Test
    fun `percent and scaling`() {
        assertEquals(50, ActionParams.percent("50%"))
        assertNull(ActionParams.percent("101"))
        assertNull(ActionParams.percent("loud"))
        assertEquals(128, ActionParams.scale(50, 255))
        assertEquals(0, ActionParams.scale(0, 15))
        assertEquals(15, ActionParams.scale(100, 15))
    }

    @Test
    fun `switch targets`() {
        assertEquals(true, ActionParams.switchTarget("toggle", current = false))
        assertEquals(false, ActionParams.switchTarget("Off", current = false))
        assertNull(ActionParams.switchTarget("maybe", current = true))
    }

    @Test
    fun `time seconds stream and ringer parsing`() {
        assertEquals(7 to 30, ActionParams.hourMinute("07:30"))
        assertNull(ActionParams.hourMinute("24:00"))
        assertNull(ActionParams.hourMinute("7.30"))
        assertEquals(60, ActionParams.seconds("60", 1800))
        assertNull(ActionParams.seconds("0", 1800))
        assertEquals(ActionParams.Stream.ALARM, ActionParams.stream("alarm"))
        assertEquals(ActionParams.Ringer.VIBRATE, ActionParams.ringer("VIBRATE"))
        assertNull(ActionParams.ringer("loud"))
    }

    @Test
    fun `battery status names`() {
        assertEquals("charging", BuiltinVariables.batteryStatus(2))
        assertEquals("full", BuiltinVariables.batteryStatus(5))
        assertEquals("unknown", BuiltinVariables.batteryStatus(99))
    }
}
