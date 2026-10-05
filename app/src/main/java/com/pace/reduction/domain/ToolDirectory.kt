package com.pace.reduction.domain

/**
 * Every tool in the app that something other than the toolkit shelf may open by name: the coach
 * in a chat reply, the "pick for me" agent, the stats that say what has worked before.
 *
 * Ids are the same strings a finished session is filed under, so the record of what someone used
 * and the menu the model chooses from can never drift apart. Descriptions reach the model, which
 * is why none of them names the substance or the feeling — the same rule as everything else the
 * coach is given.
 */
object ToolDirectory {
    const val BREATHING = "BREATHING"
    const val URGE_SURF = "URGE_SURF"
    const val GROUNDING = "GROUNDING"
    const val CHANGE_PLACE = "CHANGE_PLACE"
    const val BLOCKS = "BLOCKS"
    const val MEMORY = "MEMORY"
    const val SEQUENCE = "SEQUENCE"
    const val STORY = "STORY"
    const val TRIVIA = "TRIVIA"
    const val EMOJI = "EMOJI"

    /** Prefix for a movement session id, matching what a finished session is filed under. */
    const val MOVE_PREFIX = "MOVE:"

    enum class Kind { CALM, GAME, ARCADE, MOVE }

    data class Entry(
        val id: String,
        val kind: Kind,
        val minutes: Int,
        /** Neutral, model-facing description. Never shown to the user; the UI has its own copy. */
        val description: String,
        /** True when the tool needs the AI coach to be set up before it can run. */
        val needsCoach: Boolean = false,
    )

    private val tools = listOf(
        Entry(BREATHING, Kind.CALM, 2, "a two-minute guided breathing exercise with a slow visual rhythm"),
        Entry(URGE_SURF, Kind.CALM, 3, "watching a wave of feeling rise, crest and fall away, with a guide"),
        Entry(GROUNDING, Kind.CALM, 2, "5-4-3-2-1 senses grounding: name things you can see, hear and touch"),
        Entry(CHANGE_PLACE, Kind.CALM, 2, "a short guided change of scene: stand up, go somewhere else, reset"),
        Entry(BLOCKS, Kind.GAME, 3, "a falling-blocks puzzle game"),
        Entry(MEMORY, Kind.GAME, 2, "a card-matching memory game"),
        Entry(SEQUENCE, Kind.GAME, 1, "a quick tap-the-numbers-in-order game"),
        Entry(STORY, Kind.ARCADE, 4, "an interactive choose-your-path mini story they steer", needsCoach = true),
        Entry(TRIVIA, Kind.ARCADE, 3, "a five-question trivia sprint on a topic they pick"),
        Entry(EMOJI, Kind.ARCADE, 3, "decoding emoji puzzles that spell out films, sayings and things"),
    )

    private val moves = MoveCatalogue.shelf.map { session ->
        Entry(
            id = MOVE_PREFIX + session.id,
            kind = Kind.MOVE,
            minutes = session.minutes,
            description = moveDescription(session.id, session.minutes),
        )
    }

    val all: List<Entry> = tools + moves

    fun byId(id: String?): Entry? = id?.let { wanted -> all.firstOrNull { it.id.equals(wanted.trim(), ignoreCase = true) } }

    /** The ids available right now: arcade tools that need the coach drop out when it is off. */
    fun available(coachReady: Boolean): List<Entry> = all.filter { coachReady || !it.needsCoach }

    /** One line per tool, the form the model reads its options in. */
    fun menu(coachReady: Boolean = true): String = available(coachReady)
        .joinToString("\n") { "- ${it.id}: ${it.description} (~${it.minutes} min)" }

    private fun moveDescription(id: String, minutes: Int): String = when (id) {
        MoveCatalogue.WALK_RESET -> "a $minutes-minute guided walk, ideally outside"
        MoveCatalogue.WALK_LONG -> "a proper $minutes-minute walk"
        MoveCatalogue.RUN_INTERVALS -> "easy running intervals"
        MoveCatalogue.PUSHUP_LADDER -> "a short push-up ladder"
        MoveCatalogue.CIRCUIT -> "a quick bodyweight circuit"
        MoveCatalogue.YOGA_MORNING -> "a gentle morning mobility flow"
        MoveCatalogue.YOGA_WIND_DOWN -> "a slow wind-down stretch for the evening"
        MoveCatalogue.DESK_RESET -> "a three-minute desk reset that needs no floor space"
        else -> "a short guided movement session"
    }
}
