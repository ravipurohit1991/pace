package com.pace.reduction.domain

/**
 * Lets the coach hand over something to *do*, not just something to read.
 *
 * A reply may end with one tag such as `[[do:BREATHING]]`. The tag never reaches the screen: the
 * bubble shows the words and a button that opens that tool. A plain-text tag rather than a native
 * tool call, because it streams, survives every model Ollama hosts, and replays harmlessly in the
 * history so the model keeps seeing the convention it is expected to follow.
 */
object CoachActions {
    private val COMPLETE_TAG = Regex("""\[\[\s*do\s*:\s*([A-Za-z0-9_:\-]{1,40})\s*]]""", RegexOption.IGNORE_CASE)

    /** An unfinished tag at the very end of a streaming reply, e.g. `[[do:BRE`. */
    private val TRAILING_PARTIAL = Regex("""\[(\[[^\]]{0,48}]?)?$""")

    /** The tool the reply points at, or null when it names nothing the app can open. */
    fun extract(text: String, coachReady: Boolean = true): ToolDirectory.Entry? =
        COMPLETE_TAG.findAll(text)
            .mapNotNull { ToolDirectory.byId(it.groupValues[1]) }
            .lastOrNull { coachReady || !it.needsCoach }

    /** What the bubble shows: every tag removed, including half of one still arriving. */
    fun strip(text: String): String = text
        .replace(COMPLETE_TAG, "")
        .replace(TRAILING_PARTIAL, "")
        .replace(Regex("[ \\t]+\n"), "\n")
        .trimEnd()

    /**
     * Appended to the chat instruction. Kept as its own block so a user's custom persona keeps
     * working: the persona decides the voice, this decides what the voice can reach for.
     */
    fun instruction(coachReady: Boolean = true): String =
        "You can open one tool inside the app for them. When — and only when — a concrete thing " +
            "to do would genuinely help right now, end your message with exactly one tag in the " +
            "form [[do:ID]] using an ID from this list. Mention what it is in your own words; the " +
            "tag itself is invisible to them and becomes a button. Most messages need no tag. " +
            "Never use more than one, and never invent an ID.\n" +
            ToolDirectory.menu(coachReady)
}
