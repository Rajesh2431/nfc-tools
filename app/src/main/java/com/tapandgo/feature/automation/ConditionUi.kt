package com.tapandgo.feature.automation

import com.tapandgo.automation.engine.Condition

/** Editable shape of a condition: several comparisons joined by ALL/ANY, optionally negated. */
data class ConditionUi(val all: Boolean = true, val negate: Boolean = false, val items: List<Condition.Compare> = listOf(Condition.Compare("", Condition.Op.EQUALS, ""))) {
    fun toCondition(): Condition {
        val inner = if (items.size == 1) items.single() else if (all) Condition.And(items) else Condition.Or(items)
        return if (negate) Condition.Not(inner) else inner
    }

    companion object {
        /** null when the condition is more complex than the editor can show (it is kept unchanged). */
        fun from(c: Condition): ConditionUi? {
            val (neg, inner) = if (c is Condition.Not) true to c.condition else false to c
            return when (inner) {
                is Condition.Compare -> ConditionUi(true, neg, listOf(inner))
                is Condition.And -> inner.all.takeIf { l -> l.isNotEmpty() && l.all { it is Condition.Compare } }?.let { ConditionUi(true, neg, it.map { x -> x as Condition.Compare }) }
                is Condition.Or -> inner.any.takeIf { l -> l.isNotEmpty() && l.all { it is Condition.Compare } }?.let { ConditionUi(false, neg, it.map { x -> x as Condition.Compare }) }
                is Condition.Not -> null
            }
        }

        fun describe(c: Condition): String = when (c) {
            is Condition.Compare -> "${c.left} ${opSymbol(c.op)} ${c.right}".trim()
            is Condition.And -> c.all.joinToString(" AND ") { "(" + describe(it) + ")" }
            is Condition.Or -> c.any.joinToString(" OR ") { "(" + describe(it) + ")" }
            is Condition.Not -> "NOT (" + describe(c.condition) + ")"
        }

        fun opSymbol(op: Condition.Op) = when (op) {
            Condition.Op.EQUALS -> "="
            Condition.Op.NOT_EQUALS -> "≠"
            Condition.Op.GREATER -> ">"
            Condition.Op.GREATER_OR_EQUAL -> "≥"
            Condition.Op.LESS -> "<"
            Condition.Op.LESS_OR_EQUAL -> "≤"
            Condition.Op.CONTAINS -> "contains"
            Condition.Op.STARTS_WITH -> "starts with"
            Condition.Op.ENDS_WITH -> "ends with"
            Condition.Op.IS_EMPTY -> "is empty"
            Condition.Op.IS_NOT_EMPTY -> "is not empty"
        }
    }
}
