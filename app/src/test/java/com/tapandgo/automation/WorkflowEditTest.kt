package com.tapandgo.automation

import com.tapandgo.automation.engine.Block
import com.tapandgo.automation.engine.Condition
import com.tapandgo.automation.engine.Workflow
import com.tapandgo.automation.engine.WorkflowEdit
import com.tapandgo.automation.engine.WorkflowEdit.ListRef
import com.tapandgo.automation.engine.WorkflowEdit.Slot
import com.tapandgo.automation.engine.WorkflowJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowEditTest {
    private fun a(id: String) = Block.Action("log", mapOf("t" to id))
    private val cond = Condition.Compare("1", Condition.Op.EQUALS, "1")
    private val root = listOf(
        a("first"),
        Block.If(cond, then = listOf(a("then")), elseIfs = listOf(Block.ElseIf(cond, listOf(a("elif")))), otherwise = listOf(a("else"))),
        Block.Repeat("2", listOf(a("body"))),
    )

    @Test
    fun `insert into nested slots`() {
        val r1 = WorkflowEdit.insert(root, ListRef(listOf(1 to Slot.THEN)), a("then2"))
        assertEquals(listOf(a("then"), a("then2")), (r1[1] as Block.If).then)
        val r2 = WorkflowEdit.insert(root, ListRef(listOf(1 to Slot.THEN), elseIf = 0), a("elif2"), at = 0)
        assertEquals(a("elif2"), (r2[1] as Block.If).elseIfs[0].then.first())
        val r3 = WorkflowEdit.insert(root, ListRef(listOf(2 to Slot.BODY)), a("body2"))
        assertEquals(2, (r3[2] as Block.Repeat).body.size)
        val r4 = WorkflowEdit.insert(root, ListRef(listOf(1 to Slot.OTHERWISE)), a("else2"))
        assertEquals(2, (r4[1] as Block.If).otherwise.size)
    }

    @Test
    fun `move remove duplicate toggle replace`() {
        assertEquals(root[1], WorkflowEdit.move(root, ListRef(), 1, -1)[0])
        assertEquals(2, WorkflowEdit.remove(root, ListRef(), 0).size)
        assertEquals(4, WorkflowEdit.duplicate(root, ListRef(), 0).size)
        assertFalse(WorkflowEdit.toggle(root, ListRef(), 2)[2].enabled)
        val replaced = WorkflowEdit.replace(root, ListRef(listOf(2 to Slot.BODY)), 0, a("new"))
        assertEquals(a("new"), (replaced[2] as Block.Repeat).body[0])
    }

    @Test
    fun `invalid references leave the tree unchanged`() {
        assertEquals(root, WorkflowEdit.insert(root, ListRef(listOf(0 to Slot.BODY)), a("x"))) // action has no body
        assertEquals(root, WorkflowEdit.remove(root, ListRef(), 9))
        assertEquals(root, WorkflowEdit.move(root, ListRef(), 0, -1))
    }

    @Test
    fun `count and depth`() {
        assertEquals(7, WorkflowEdit.count(root)) // 1 + (If + 3 children) + (Repeat + 1)
        assertEquals(2, WorkflowEdit.depth(root))
    }

    @Test
    fun `json validation enforces allowlist, names, size and depth`() {
        val w = Workflow("Morning", root)
        val text = WorkflowJson.encode(w)
        assertEquals(w, WorkflowJson.decode(text, setOf("log")))
        assertNull(WorkflowJson.decode(text, setOf("other")))
        assertNull(WorkflowJson.decode(text.replace("\"type\":\"action\"", "\"type\":\"shell\""), setOf("log")))
        assertEquals(WorkflowJson.Problem.NAME, WorkflowJson.validate(w.copy(name = ""), setOf("log")))
        assertEquals(WorkflowJson.Problem.BAD_VARIABLE, WorkflowJson.validate(w.copy(blocks = listOf(Block.SetVariable("1bad", "x"))), setOf("log")))
        var deep: List<Block> = listOf(a("x"))
        repeat(13) { deep = listOf(Block.Repeat("1", deep)) }
        assertEquals(WorkflowJson.Problem.TOO_DEEP, WorkflowJson.validate(Workflow("d", deep), setOf("log")))
        assertNotNull(WorkflowJson.decode(WorkflowJson.encode(Workflow("ok", listOf(Block.Delay("10")))), emptySet()))
    }
}
