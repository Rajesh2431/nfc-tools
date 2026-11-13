package com.tapandgo.automation

import com.tapandgo.automation.engine.WorkflowJson
import com.tapandgo.feature.automation.ActionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowTemplatesTest {
    @Test
    fun `every template is valid, round-trips JSON and uses only catalogued actions`() {
        val allowed = ActionCatalog.all.map { it.id }.toSet()
        WorkflowTemplates.all.forEach { t ->
            assertNull(t.id, WorkflowJson.validate(t.workflow, allowed))
            assertEquals(t.id, t.workflow, WorkflowJson.decode(WorkflowJson.encode(t.workflow), allowed))
        }
        assertEquals(WorkflowTemplates.all.size, WorkflowTemplates.all.map { it.id }.toSet().size)
    }
}
