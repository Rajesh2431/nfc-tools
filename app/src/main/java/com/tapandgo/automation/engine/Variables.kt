package com.tapandgo.automation.engine

/**
 * ${NAME} interpolation. Names are [A-Z0-9_], case-insensitive, max 64 chars.
 * "$${" escapes a literal "${". Unknown variables resolve to "" and are
 * reported so the UI can warn; nothing is ever evaluated as code.
 */
object Variables {
    private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
    private val TOKEN = Regex("\\$\\$\\{|\\$\\{([A-Za-z_][A-Za-z0-9_]{0,63})}")

    fun isValidName(n: String) = NAME.matches(n)

    data class Result(val text: String, val missing: Set<String>)

    fun interpolate(template: String, lookup: (String) -> String?): Result {
        val missing = mutableSetOf<String>()
        val out = TOKEN.replace(template) { m ->
            if (m.value == "$\${") {
                "\${"
            } else {
                val name = m.groupValues[1].uppercase()
                lookup(name) ?: run { missing += name; "" }
            }
        }
        return Result(out, missing)
    }

    /** Names referenced by a template (for availability/permission checks before running). */
    fun referenced(template: String): Set<String> =
        TOKEN.findAll(template).mapNotNull { m -> m.groupValues[1].takeIf { it.isNotEmpty() }?.uppercase() }.toSet()

    /** Replaces secret values in log text. */
    fun mask(text: String, secrets: Collection<String>): String =
        secrets.filter { it.length >= 3 }.sortedByDescending { it.length }.fold(text) { acc, s -> acc.replace(s, "•••") }
}
