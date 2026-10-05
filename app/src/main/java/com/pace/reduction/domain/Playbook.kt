package com.pace.reduction.domain

/**
 * One implementation intention: "if this happens, then I do that".
 *
 * Deciding in advance is one of the better-evidenced tricks in behaviour change — the plan fires
 * on the cue instead of waiting for a decision to be made in the worst possible minute to make
 * one. The coach drafts them from the person's own patterns; the person keeps, edits or bins them.
 */
data class IfThenPlan(val cue: String, val action: String) {
    fun encode(): String = Playbook.encode(this)
}

object Playbook {
    const val MAX_PLANS = 6
    const val MAX_PART_CHARS = 90

    /** Unit separator: never typed by a person, so a plan cannot be split in the wrong place. */
    private const val SEPARATOR = '\u001F'

    fun encode(plan: IfThenPlan): String = clean(plan.cue) + SEPARATOR + clean(plan.action)

    fun decode(stored: String): IfThenPlan? {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 2) return null
        val cue = clean(parts[0])
        val action = clean(parts[1])
        return if (cue.isEmpty() || action.isEmpty()) null else IfThenPlan(cue, action)
    }

    /** Trimmed, capped and de-duplicated, so nothing unbounded reaches the preferences store. */
    fun sanitise(plans: List<IfThenPlan>): List<IfThenPlan> = plans
        .map { IfThenPlan(clean(it.cue), clean(it.action)) }
        .filter { it.cue.isNotEmpty() && it.action.isNotEmpty() }
        .distinctBy { it.cue.lowercase() to it.action.lowercase() }
        .take(MAX_PLANS)

    /**
     * Reads the coach's suggestions. Leading "If"/"then" are dropped because the UI supplies its
     * own, and a plan that names the substance is discarded rather than shown on the home screen.
     */
    fun parseSuggestions(raw: String): List<IfThenPlan> {
        val cleaned = ArcadeParser.stripFences(raw)
        val pattern = Regex(
            """\{\s*"if"\s*:\s*"((?:[^"\\]|\\.){1,200})"\s*,\s*"then"\s*:\s*"((?:[^"\\]|\\.){1,200})"\s*}""",
        )
        return sanitise(
            pattern.findAll(cleaned).map { match ->
                IfThenPlan(
                    cue = unescape(match.groupValues[1]).removePrefixIgnoringCase("if "),
                    action = unescape(match.groupValues[2]).removePrefixIgnoringCase("then "),
                )
            }
                .filterNot { TriggerWords.contains(it.cue) || TriggerWords.contains(it.action) }
                .toList(),
        ).take(3)
    }

    val schema = ArcadeParser.Schemas.objectSchema(
        "plans" to ArcadeParser.Schemas.arraySchema(
            ArcadeParser.Schemas.objectSchema(
                "if" to ArcadeParser.Schemas.stringSchema(),
                "then" to ArcadeParser.Schemas.stringSchema(),
            ),
        ),
    )

    private fun clean(text: String): String = text
        .replace(SEPARATOR, ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', ',')
        .take(MAX_PART_CHARS)

    private fun unescape(text: String): String = text.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun String.removePrefixIgnoringCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
