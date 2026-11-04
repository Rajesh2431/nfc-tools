package com.tapandgo.automation.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of one action invocation. Unsupported is truthful "this device/app cannot do it". */
sealed interface ActionResult {
    data class Success(val output: String? = null) : ActionResult
    data class Failure(val reason: String) : ActionResult
    data class Unsupported(val reason: String) : ActionResult
}

/** An allowlisted action implementation. Params are already interpolated. */
fun interface ActionHandler {
    suspend fun run(params: Map<String, String>): ActionResult
}

/** Registry of allowlisted actions; unknown ids are refused. */
class ActionRegistry(private val handlers: Map<String, ActionHandler>) {
    operator fun get(id: String): ActionHandler? = handlers[id]
    val ids: Set<String> get() = handlers.keys
}

enum class RunStatus { SUCCESS, STOPPED, FAILED, TIMED_OUT, LIMIT_EXCEEDED }

data class StepLog(val path: String, val actionId: String, val status: String, val detail: String? = null)

data class RunResult(val status: RunStatus, val log: List<StepLog>, val variables: Map<String, String>, val error: String? = null)

/**
 * Executes a [Workflow]. Built-in variables come from [builtins] (read lazily,
 * so only referenced values are computed); user variables override nothing
 * built-in. Every action result is logged with secrets masked.
 */
class WorkflowEngine(
    private val registry: ActionRegistry,
    private val builtins: (String) -> String? = { null },
    private val secrets: () -> Collection<String> = { emptyList() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private class Abort(val status: RunStatus, val reason: String? = null) : Exception()
    private object BreakSignal : Exception()
    private object ContinueSignal : Exception()

    private class Ctx(val settings: ExecutionSettings, val vars: MutableMap<String, String>) {
        var steps = 0
        val log = mutableListOf<StepLog>()
    }

    suspend fun run(workflow: Workflow): RunResult {
        val vars = workflow.variables.filterKeys(Variables::isValidName).mapKeys { it.key.uppercase() }.toMutableMap()
        val ctx = Ctx(workflow.settings, vars)
        val status = try {
            withTimeoutOrNull(workflow.settings.timeoutMs) {
                runBlocks(workflow.blocks, ctx, "")
                RunStatus.SUCCESS
            } ?: RunStatus.TIMED_OUT
        } catch (a: Abort) {
            return RunResult(a.status, ctx.log.toList(), ctx.vars.toMap(), a.reason)
        } catch (_: BreakSignal) {
            RunStatus.SUCCESS
        } catch (_: ContinueSignal) {
            RunStatus.SUCCESS
        }
        return RunResult(status, ctx.log.toList(), ctx.vars.toMap())
    }

    private fun lookup(ctx: Ctx, name: String): String? = ctx.vars[name] ?: builtins(name)

    private fun text(ctx: Ctx, t: String) = Variables.interpolate(t) { lookup(ctx, it) }.text

    private fun tick(ctx: Ctx) {
        if (++ctx.steps > ctx.settings.maxSteps) throw Abort(RunStatus.LIMIT_EXCEEDED, "step limit ${ctx.settings.maxSteps}")
    }

    private suspend fun runBlocks(blocks: List<Block>, ctx: Ctx, path: String) {
        blocks.forEachIndexed { i, b -> if (b.enabled) runBlock(b, ctx, "$path${i + 1}") }
    }

    private suspend fun runBlock(b: Block, ctx: Ctx, path: String) {
        tick(ctx)
        when (b) {
            is Block.Action -> runAction(b, ctx, path)
            is Block.If -> {
                val branch = when {
                    Conditions.evaluate(b.condition) { text(ctx, it) } -> b.then
                    else -> b.elseIfs.firstOrNull { Conditions.evaluate(it.condition) { t -> text(ctx, t) } }?.then ?: b.otherwise
                }
                runBlocks(branch, ctx, "$path.")
            }

            is Block.Repeat -> {
                val n = text(ctx, b.times).trim().toIntOrNull()?.coerceAtLeast(0) ?: throw Abort(RunStatus.FAILED, "repeat count is not a number")
                if (n > ctx.settings.maxLoopIterations) throw Abort(RunStatus.LIMIT_EXCEEDED, "repeat $n > ${ctx.settings.maxLoopIterations}")
                loop@ for (i in 0 until n) {
                    b.indexVariable?.takeIf(Variables::isValidName)?.let { ctx.vars[it.uppercase()] = i.toString() }
                    try { runBlocks(b.body, ctx, "$path.") } catch (_: BreakSignal) { break@loop } catch (_: ContinueSignal) { continue@loop }
                }
            }

            is Block.While -> {
                var i = 0
                loop@ while (Conditions.evaluate(b.condition) { text(ctx, it) }) {
                    if (++i > ctx.settings.maxLoopIterations) throw Abort(RunStatus.LIMIT_EXCEEDED, "while exceeded ${ctx.settings.maxLoopIterations} iterations")
                    try { runBlocks(b.body, ctx, "$path.") } catch (_: BreakSignal) { break@loop } catch (_: ContinueSignal) { continue@loop }
                }
            }

            is Block.Delay -> {
                val ms = text(ctx, b.millis).trim().toLongOrNull() ?: throw Abort(RunStatus.FAILED, "delay is not a number")
                sleep(ms.coerceIn(0, 10 * 60_000))
            }

            is Block.SetVariable -> {
                if (!Variables.isValidName(b.name)) throw Abort(RunStatus.FAILED, "invalid variable name")
                ctx.vars[b.name.uppercase()] = text(ctx, b.value).take(10_000)
            }

            is Block.Break -> throw BreakSignal
            is Block.Continue -> throw ContinueSignal
            is Block.Stop -> throw Abort(RunStatus.STOPPED)
        }
    }

    private suspend fun runAction(a: Block.Action, ctx: Ctx, path: String) {
        val handler = registry[a.actionId] ?: run {
            ctx.log += StepLog(path, a.actionId, "REFUSED", "not in allowlist")
            throw Abort(RunStatus.FAILED, "unknown action ${a.actionId}")
        }
        val params = a.params.mapValues { (_, v) -> text(ctx, v) }
        var attempt = 0
        var result: ActionResult
        do {
            result = handler.run(params)
            attempt++
        } while (result is ActionResult.Failure && attempt <= a.retries.coerceIn(0, 5))
        val masked = { s: String? -> s?.let { Variables.mask(it, secrets()) } }
        when (result) {
            is ActionResult.Success -> {
                ctx.log += StepLog(path, a.actionId, "OK", masked(result.output))
                a.outputVariable?.takeIf(Variables::isValidName)?.let { ctx.vars[it.uppercase()] = result.output.orEmpty() }
            }

            is ActionResult.Failure, is ActionResult.Unsupported -> {
                val reason = if (result is ActionResult.Failure) result.reason else (result as ActionResult.Unsupported).reason
                ctx.log += StepLog(path, a.actionId, if (result is ActionResult.Failure) "FAILED" else "UNSUPPORTED", masked(reason))
                if (!a.continueOnError && ctx.settings.stopOnError) throw Abort(RunStatus.FAILED, masked(reason))
            }
        }
    }
}

object Conditions {
    fun evaluate(c: Condition, text: (String) -> String): Boolean = when (c) {
        is Condition.And -> c.all.all { evaluate(it, text) }
        is Condition.Or -> c.any.any { evaluate(it, text) }
        is Condition.Not -> !evaluate(c.condition, text)
        is Condition.Compare -> compare(text(c.left), c.op, text(c.right))
    }

    /** Numeric comparison when both sides are numbers, otherwise case-sensitive text. */
    fun compare(l: String, op: Condition.Op, r: String): Boolean {
        val ln = l.trim().toDoubleOrNull()
        val rn = r.trim().toDoubleOrNull()
        val cmp = if (ln != null && rn != null) ln.compareTo(rn) else l.compareTo(r)
        return when (op) {
            Condition.Op.EQUALS -> if (ln != null && rn != null) ln == rn else l == r
            Condition.Op.NOT_EQUALS -> if (ln != null && rn != null) ln != rn else l != r
            Condition.Op.GREATER -> cmp > 0
            Condition.Op.GREATER_OR_EQUAL -> cmp >= 0
            Condition.Op.LESS -> cmp < 0
            Condition.Op.LESS_OR_EQUAL -> cmp <= 0
            Condition.Op.CONTAINS -> l.contains(r)
            Condition.Op.STARTS_WITH -> l.startsWith(r)
            Condition.Op.ENDS_WITH -> l.endsWith(r)
            Condition.Op.IS_EMPTY -> l.isBlank()
            Condition.Op.IS_NOT_EMPTY -> l.isNotBlank()
        }
    }
}
