package com.tapandgo.automation.engine

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Immutable edits on the block tree for the visual builder. A [Path] is a
 * list of steps: each step is an index into a block list plus, for nested
 * containers, which child list (THEN, ELSE_IF(n), OTHERWISE, BODY) to enter.
 */
object WorkflowEdit {
    enum class Slot { THEN, OTHERWISE, BODY }

    /** Address of a block list: container path + slot (empty = root list). */
    data class ListRef(val steps: List<Pair<Int, Slot>> = emptyList(), val elseIf: Int? = null)

    private fun children(b: Block, slot: Slot, elseIf: Int?): List<Block>? = when (b) {
        is Block.If -> when {
            elseIf != null -> b.elseIfs.getOrNull(elseIf)?.then
            slot == Slot.THEN -> b.then
            slot == Slot.OTHERWISE -> b.otherwise
            else -> null
        }
        is Block.Repeat -> b.body.takeIf { slot == Slot.BODY }
        is Block.While -> b.body.takeIf { slot == Slot.BODY }
        else -> null
    }

    private fun withChildren(b: Block, slot: Slot, elseIf: Int?, list: List<Block>): Block = when (b) {
        is Block.If -> when {
            elseIf != null -> b.copy(elseIfs = b.elseIfs.mapIndexed { i, e -> if (i == elseIf) e.copy(then = list) else e })
            slot == Slot.THEN -> b.copy(then = list)
            else -> b.copy(otherwise = list)
        }
        is Block.Repeat -> b.copy(body = list)
        is Block.While -> b.copy(body = list)
        else -> b
    }

    /** Applies [f] to the block list addressed by [ref]; invalid refs leave the tree unchanged. */
    fun mapList(root: List<Block>, ref: ListRef, f: (List<Block>) -> List<Block>): List<Block> {
        fun go(list: List<Block>, depth: Int): List<Block> {
            if (depth == ref.steps.size) return f(list)
            val (index, slot) = ref.steps[depth]
            val b = list.getOrNull(index) ?: return list
            val last = depth == ref.steps.lastIndex
            val kids = children(b, slot, if (last) ref.elseIf else null) ?: return list
            return list.toMutableList().also { it[index] = withChildren(b, slot, if (last) ref.elseIf else null, go(kids, depth + 1)) }
        }
        return go(root, 0)
    }

    fun insert(root: List<Block>, ref: ListRef, block: Block, at: Int? = null) =
        mapList(root, ref) { l -> l.toMutableList().also { it.add((at ?: l.size).coerceIn(0, l.size), block) } }

    fun remove(root: List<Block>, ref: ListRef, index: Int) =
        mapList(root, ref) { l -> if (index in l.indices) l.filterIndexed { i, _ -> i != index } else l }

    fun move(root: List<Block>, ref: ListRef, index: Int, delta: Int) = mapList(root, ref) { l ->
        val j = index + delta
        if (index !in l.indices || j !in l.indices) l else l.toMutableList().also { it.add(j, it.removeAt(index)) }
    }

    fun replace(root: List<Block>, ref: ListRef, index: Int, block: Block) =
        mapList(root, ref) { l -> if (index in l.indices) l.toMutableList().also { it[index] = block } else l }

    fun duplicate(root: List<Block>, ref: ListRef, index: Int) =
        mapList(root, ref) { l -> if (index in l.indices) l.toMutableList().also { it.add(index + 1, l[index]) } else l }

    fun toggle(root: List<Block>, ref: ListRef, index: Int) = mapList(root, ref) { l ->
        if (index !in l.indices) l else l.toMutableList().also { it[index] = setEnabled(l[index], !l[index].enabled) }
    }

    private fun setEnabled(b: Block, e: Boolean): Block = when (b) {
        is Block.Action -> b.copy(enabled = e)
        is Block.If -> b.copy(enabled = e)
        is Block.Repeat -> b.copy(enabled = e)
        is Block.While -> b.copy(enabled = e)
        is Block.Delay -> b.copy(enabled = e)
        is Block.SetVariable -> b.copy(enabled = e)
        is Block.Break -> b.copy(enabled = e)
        is Block.Continue -> b.copy(enabled = e)
        is Block.Stop -> b.copy(enabled = e)
    }

    fun count(blocks: List<Block>): Int = blocks.sumOf { b ->
        1 + when (b) {
            is Block.If -> count(b.then) + b.elseIfs.sumOf { count(it.then) } + count(b.otherwise)
            is Block.Repeat -> count(b.body)
            is Block.While -> count(b.body)
            else -> 0
        }
    }

    fun depth(blocks: List<Block>): Int = blocks.maxOfOrNull { b ->
        1 + when (b) {
            is Block.If -> maxOf(depth(b.then), b.elseIfs.maxOfOrNull { depth(it.then) } ?: 0, depth(b.otherwise))
            is Block.Repeat -> depth(b.body)
            is Block.While -> depth(b.body)
            else -> 0
        }
    } ?: 0
}

/** Strict JSON + structural validation for stored/imported workflows. */
object WorkflowJson {
    const val MAX_BLOCKS = 500
    const val MAX_DEPTH = 12
    val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = false; encodeDefaults = true }

    fun encode(w: Workflow): String = json.encodeToString(Workflow.serializer(), w)

    /** @return null if malformed, too large, too deep, or uses actions outside [allowed]. */
    fun decode(text: String, allowed: Set<String>): Workflow? {
        if (text.length > 512 * 1024) return null
        val w = runCatching { json.decodeFromString(Workflow.serializer(), text) }.getOrNull() ?: return null
        return w.takeIf { validate(it, allowed) == null }
    }

    enum class Problem { NAME, TOO_MANY_BLOCKS, TOO_DEEP, UNKNOWN_ACTION, BAD_VARIABLE }

    fun validate(w: Workflow, allowed: Set<String>): Problem? {
        if (w.name.isBlank() || w.name.length > 80) return Problem.NAME
        if (WorkflowEdit.count(w.blocks) > MAX_BLOCKS) return Problem.TOO_MANY_BLOCKS
        if (WorkflowEdit.depth(w.blocks) > MAX_DEPTH) return Problem.TOO_DEEP
        if (!w.variables.keys.all(Variables::isValidName)) return Problem.BAD_VARIABLE
        fun walk(list: List<Block>): Problem? {
            for (b in list) {
                val p = when (b) {
                    is Block.Action -> if (b.actionId !in allowed) Problem.UNKNOWN_ACTION else null
                    is Block.SetVariable -> if (!Variables.isValidName(b.name)) Problem.BAD_VARIABLE else null
                    is Block.If -> walk(b.then) ?: b.elseIfs.firstNotNullOfOrNull { walk(it.then) } ?: walk(b.otherwise)
                    is Block.Repeat -> walk(b.body)
                    is Block.While -> walk(b.body)
                    else -> null
                }
                if (p != null) return p
            }
            return null
        }
        return walk(w.blocks)
    }

    @Suppress("unused")
    fun blocksSerializer() = ListSerializer(Block.serializer())
}
