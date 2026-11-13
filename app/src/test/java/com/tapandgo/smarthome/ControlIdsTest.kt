package com.tapandgo.smarthome

import com.tapandgo.automation.LaunchDecision
import com.tapandgo.automation.LaunchPolicy
import com.tapandgo.automation.LaunchRequest
import com.tapandgo.automation.RunSource
import com.tapandgo.automation.WorkflowLaunch
import com.tapandgo.automation.WorkflowTriggers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlIdsTest {
    @Test
    fun `control ids round trip and reject garbage`() {
        assertEquals(ControlIds.Parsed.Ha(3, "light.kitchen"), ControlIds.parse(ControlIds.ha(3, "light.kitchen")))
        assertEquals(ControlIds.Parsed.Hue(1, "7"), ControlIds.parse(ControlIds.hue(1, "7")))
        assertEquals(ControlIds.Parsed.Device(9), ControlIds.parse(ControlIds.device(9)))
        assertEquals(ControlIds.Parsed.Workflow(4), ControlIds.parse(ControlIds.workflow(4)))
        assertNull(ControlIds.parse("ha:x:light.a"))
        assertNull(ControlIds.parse("ha:1:Bad Entity"))
        assertNull(ControlIds.parse("hue:1:../2"))
        assertNull(ControlIds.parse("shell:rm"))
    }

    @Test
    fun `widget source is internal only and runs directly`() {
        val t = "0f8fad5b-d9cb-469f-a165-70867728950e"
        assertEquals(LaunchRequest(t, RunSource.WIDGET), WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, WorkflowLaunch.INTERNAL_ALIAS, t, "WIDGET"))
        assertEquals(LaunchRequest(t, RunSource.EXTERNAL), WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, WorkflowLaunch.EXTERNAL_ALIAS, t, "WIDGET"))
        assertNull(WorkflowLaunch.parse(WorkflowLaunch.ACTION_RUN, WorkflowLaunch.INTERNAL_ALIAS, t, "CONTEXT"))
        assertEquals(LaunchDecision.RUN, LaunchPolicy.decide(RunSource.WIDGET, WorkflowTriggers(t), false))
    }
}
