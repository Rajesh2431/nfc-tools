package com.tapandgo.automation.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A workflow is a tree of blocks. Everything is plain, serializable data:
 * action ids are looked up in an allowlisted registry and parameters are
 * strings with ${VAR} templates — no code, scripts or intents come from data.
 */
@Serializable
data class Workflow(
    val name: String,
    val blocks: List<Block>,
    val variables: Map<String, String> = emptyMap(),
    val settings: ExecutionSettings = ExecutionSettings(),
)

@Serializable
data class ExecutionSettings(
    val timeoutMs: Long = 60_000,
    val stopOnError: Boolean = true,
    val maxSteps: Int = 10_000,
    val maxLoopIterations: Int = 1_000,
)

@Serializable
sealed class Block {
    abstract val enabled: Boolean

    @Serializable
    @SerialName("action")
    data class Action(
        val actionId: String,
        val params: Map<String, String> = emptyMap(),
        /** Stores the action's primary output in this variable. */
        val outputVariable: String? = null,
        val retries: Int = 0,
        val continueOnError: Boolean = false,
        override val enabled: Boolean = true,
    ) : Block()

    @Serializable
    @SerialName("if")
    data class If(
        val condition: Condition,
        val then: List<Block>,
        val elseIfs: List<ElseIf> = emptyList(),
        val otherwise: List<Block> = emptyList(),
        override val enabled: Boolean = true,
    ) : Block()

    @Serializable
    data class ElseIf(val condition: Condition, val then: List<Block>)

    @Serializable
    @SerialName("repeat")
    data class Repeat(val times: String, val body: List<Block>, val indexVariable: String? = null, override val enabled: Boolean = true) : Block()

    @Serializable
    @SerialName("while")
    data class While(val condition: Condition, val body: List<Block>, override val enabled: Boolean = true) : Block()

    @Serializable
    @SerialName("delay")
    data class Delay(val millis: String, override val enabled: Boolean = true) : Block()

    @Serializable
    @SerialName("set")
    data class SetVariable(val name: String, val value: String, override val enabled: Boolean = true) : Block()

    @Serializable @SerialName("break") data class Break(override val enabled: Boolean = true) : Block()
    @Serializable @SerialName("continue") data class Continue(override val enabled: Boolean = true) : Block()
    @Serializable @SerialName("stop") data class Stop(override val enabled: Boolean = true) : Block()
}

@Serializable
sealed class Condition {
    @Serializable
    @SerialName("compare")
    data class Compare(val left: String, val op: Op, val right: String = "") : Condition()

    @Serializable @SerialName("and") data class And(val all: List<Condition>) : Condition()
    @Serializable @SerialName("or") data class Or(val any: List<Condition>) : Condition()
    @Serializable @SerialName("not") data class Not(val condition: Condition) : Condition()

    enum class Op { EQUALS, NOT_EQUALS, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, CONTAINS, STARTS_WITH, ENDS_WITH, IS_EMPTY, IS_NOT_EMPTY }
}
