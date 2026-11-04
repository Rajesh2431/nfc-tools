package com.tapandgo.automation

import com.tapandgo.automation.engine.ActionHandler
import com.tapandgo.automation.engine.ActionRegistry
import com.tapandgo.automation.engine.ActionResult
import com.tapandgo.automation.engine.Block
import com.tapandgo.automation.engine.Condition
import com.tapandgo.automation.engine.Condition.Op
import com.tapandgo.automation.engine.Conditions
import com.tapandgo.automation.engine.ExecutionSettings
import com.tapandgo.automation.engine.RunStatus
import com.tapandgo.automation.engine.Variables
import com.tapandgo.automation.engine.Workflow
import com.tapandgo.automation.engine.WorkflowEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowEngineTest {
    private val calls = mutableListOf<Pair<String, Map<String, String>>>()
    private var failNext = 0
    private val registry = ActionRegistry(
        mapOf(
            "log" to ActionHandler { p -> calls += "log" to p; ActionResult.Success(p["text"]) },
            "flaky" to ActionHandler { p -> calls += "flaky" to p; if (failNext-- > 0) ActionResult.Failure("boom") else ActionResult.Success("ok") },
            "nope" to ActionHandler { ActionResult.Unsupported("needs root") },
            "slow" to ActionHandler { delay(10_000); ActionResult.Success() },
        ),
    )
    private val builtins = mapOf("BATTERY_LEVEL" to "42", "DEVICE_MODEL" to "Pixel")
    private fun engine() = WorkflowEngine(registry, builtins = { builtins[it] }, secrets = { listOf("hunter2") }, sleep = {})
    private fun log(t: String) = Block.Action("log", mapOf("text" to t))

    @Test
    fun `variables interpolate builtins user values and escapes`() {
        val r = Variables.interpolate("Battery \${battery_level}% on \${DEVICE_MODEL}, \${MISSING}! $\${raw}") { builtins[it] }
        assertEquals("Battery 42% on Pixel, ! \${raw}", r.text)
        assertEquals(setOf("MISSING"), r.missing)
        assertEquals(setOf("A", "B"), Variables.referenced("\${a} and \${B}"))
        assertEquals("pw=•••", Variables.mask("pw=hunter2", listOf("hunter2")))
    }

    @Test
    fun `if else-if else picks the right branch with numeric comparison`() = runTest {
        fun wf(level: String) = Workflow(
            "w",
            listOf(
                Block.SetVariable("L", level),
                Block.If(
                    Condition.Compare("\${L}", Op.LESS, "20"), listOf(log("low")),
                    elseIfs = listOf(Block.ElseIf(Condition.Compare("\${L}", Op.LESS, "80"), listOf(log("mid")))),
                    otherwise = listOf(log("high")),
                ),
            ),
        )
        listOf("5" to "low", "42" to "mid", "100" to "high").forEach { (lvl, want) ->
            calls.clear(); engine().run(wf(lvl))
            assertEquals(want, calls.single().second["text"])
        }
    }

    @Test
    fun `and or not and string operators`() {
        val t: (String) -> String = { it }
        assertTrue(Conditions.evaluate(Condition.And(listOf(Condition.Compare("abc", Op.STARTS_WITH, "a"), Condition.Compare("abc", Op.ENDS_WITH, "c"))), t))
        assertTrue(Conditions.evaluate(Condition.Or(listOf(Condition.Compare("x", Op.IS_EMPTY), Condition.Compare("hello", Op.CONTAINS, "ell"))), t))
        assertFalse(Conditions.evaluate(Condition.Not(Condition.Compare("", Op.IS_EMPTY)), t))
        assertTrue(Conditions.compare("10", Op.GREATER, "9")) // numeric, not lexical
        assertTrue(Conditions.compare("10.0", Op.EQUALS, "10"))
        assertTrue(Conditions.compare("b", Op.GREATER, "a"))
    }

    @Test
    fun `repeat with index, break and continue`() = runTest {
        val wf = Workflow(
            "w",
            listOf(
                Block.Repeat(
                    "5",
                    listOf(
                        Block.If(Condition.Compare("\${I}", Op.EQUALS, "1"), listOf(Block.Continue())),
                        Block.If(Condition.Compare("\${I}", Op.EQUALS, "3"), listOf(Block.Break())),
                        log("i=\${I}"),
                    ),
                    indexVariable = "I",
                ),
            ),
        )
        val r = engine().run(wf)
        assertEquals(RunStatus.SUCCESS, r.status)
        assertEquals(listOf("i=0", "i=2"), calls.map { it.second["text"] })
    }

    @Test
    fun `while loop is bounded and stop ends the run`() = runTest {
        val infinite = Workflow("w", listOf(Block.While(Condition.Compare("1", Op.EQUALS, "1"), listOf(log("x")))), settings = ExecutionSettings(maxLoopIterations = 10))
        assertEquals(RunStatus.LIMIT_EXCEEDED, engine().run(infinite).status)
        calls.clear()
        val stop = engine().run(Workflow("w", listOf(log("a"), Block.Stop(), log("b"))))
        assertEquals(RunStatus.STOPPED, stop.status)
        assertEquals(1, calls.size)
    }

    @Test
    fun `retries, continue on error and stop on error`() = runTest {
        failNext = 2
        assertEquals(RunStatus.SUCCESS, engine().run(Workflow("w", listOf(Block.Action("flaky", retries = 2)))).status)
        failNext = 5
        val failed = engine().run(Workflow("w", listOf(Block.Action("flaky", retries = 1), log("after"))))
        assertEquals(RunStatus.FAILED, failed.status)
        assertTrue(calls.none { it.second["text"] == "after" })
        val tolerant = engine().run(Workflow("w", listOf(Block.Action("nope", continueOnError = true), log("after"))))
        assertEquals(RunStatus.SUCCESS, tolerant.status)
        assertEquals("UNSUPPORTED", tolerant.log.first().status)
    }

    @Test
    fun `unknown actions are refused, outputs stored, secrets masked`() = runTest {
        val r = engine().run(Workflow("w", listOf(Block.Action("log", mapOf("text" to "pw hunter2"), outputVariable = "OUT"), Block.Action("shell", mapOf("cmd" to "rm")))))
        assertEquals(RunStatus.FAILED, r.status)
        assertEquals("REFUSED", r.log.last().status)
        assertEquals("pw •••", r.log.first().detail)
        assertEquals("pw hunter2", r.variables["OUT"])
    }

    @Test
    fun `timeout and disabled blocks`() = runTest {
        val r = engine().run(Workflow("w", listOf(Block.Action("slow")), settings = ExecutionSettings(timeoutMs = 50)))
        assertEquals(RunStatus.TIMED_OUT, r.status)
        calls.clear()
        engine().run(Workflow("w", listOf(log("a").copy(enabled = false), log("b"))))
        assertEquals(listOf("b"), calls.map { it.second["text"] })
    }

    @Test
    fun `workflows round trip as strict json`() {
        val json = Json { classDiscriminator = "type" }
        val blocks = listOf(Block.If(Condition.And(listOf(Condition.Compare("\${A}", Op.GREATER, "1"))), listOf(log("x")), otherwise = listOf(Block.Delay("100"))))
        val s = json.encodeToString(ListSerializer(Block.serializer()), blocks)
        assertEquals(blocks, json.decodeFromString(ListSerializer(Block.serializer()), s))
    }
}
