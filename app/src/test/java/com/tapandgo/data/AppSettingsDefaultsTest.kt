package com.tapandgo.data

import com.tapandgo.data.settings.AppSettings
import com.tapandgo.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun `privacy and security defaults are conservative`() {
        val s = AppSettings()
        assertFalse(s.appLock)
        assertFalse(s.allowExternalTriggers)
        assertFalse(s.rootWorkflowActions)
        assertFalse(s.storeUid)
        assertFalse(s.savePayload)
        assertTrue(s.scanVibration)
        assertEquals("HOME", s.landingPage)
    }

    @Test
    fun `every landing choice is a real tab`() {
        assertEquals(TopLevelDestination.entries.map { it.name }, AppSettings.LANDING_CHOICES)
    }
}
