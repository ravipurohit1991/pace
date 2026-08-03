package com.pace.reduction.domain

import com.pace.reduction.core.network.OllamaMessage

/** The local facts the coach is allowed to reason about. Nothing else leaves the device. */
data class CoachContext(
    val countToday: Int,
    val ceiling: Int,
    val minutesSinceLastCigarette: Long?,
    val minutesUntilNextWindow: Long?,
    val zeroDayStreak: Int,
    val smokeFreeHours: Long,
    val cigarettesAvoided: Int,
    val moneySaved: Double,
    val currencyCode: String,
    val topTriggers: List<String>,
    val personalReason: String,
    val tone: String,
)

enum class CoachTask { CHAT, RIDDLE, NUDGE, INSIGHT, CHECKUP }

object CoachPrompt {
    /** Editable from the Coach screen; this is what "Reset" restores. */
    const val DEFAULT_PERSONA =
        "You are Pace — a warm, funny friend who happens to be great at helping people quit smoking. " +
            "You are texting a mate, not filing a report. 🙂\n\n" +
            "How you talk:\n" +
            "- Be genuinely playful. Quirky humour, light teasing, the occasional absurd tangent.\n" +
            "- Use emojis naturally, the way a friend does — a few, where they land, never a parade.\n" +
            "- Sound like a person: contractions, short sentences, reactions. \"Oof, 3pm slump again?\"\n" +
            "- Never open with the same stock phrase twice. Vary your rhythm and your angle.\n" +
            "- Follow the thread of the conversation. Refer back to what they already told you.\n" +
            "- No corporate cheerleading, no therapy-speak, no bullet-pointed advice dumps.\n\n" +
            "What you never do:\n" +
            "- Never shame them, moralise, or lecture. Never celebrate or encourage smoking.\n" +
            "- A delay is always a win. A lapse is never failure — you shrug it off and move on with them.\n" +
            "- You are not a doctor. For medical questions, cheerfully point them to a clinician.\n\n" +
            "Format: plain conversational text. No markdown, no headings, no bullet lists."

    fun messages(
        task: CoachTask,
        context: CoachContext?,
        history: List<OllamaMessage>,
        persona: String = DEFAULT_PERSONA,
    ): List<OllamaMessage> = buildList {
        val system = buildString {
            append(persona.ifBlank { DEFAULT_PERSONA })
            append("\n\n")
            append(instruction(task))
            if (context != null) {
                append("\n\n")
                append(facts(context))
            }
        }
        add(OllamaMessage("system", system))
        addAll(history.takeLast(MAX_HISTORY))
        if (task != CoachTask.CHAT) add(OllamaMessage("user", kickoff(task)))
    }

    private fun instruction(task: CoachTask): String = when (task) {
        CoachTask.CHAT ->
            "Keep the chat going and carry them past the craving. Two or three sentences, tops — this is " +
                "a text thread, not an essay. React to what they actually said, then leave them something " +
                "to reply to. If they sound about to smoke, hand them something concrete to do instead of " +
                "advice about smoking."
        CoachTask.RIDDLE ->
            "Give exactly one short riddle or lateral-thinking puzzle that takes about two minutes " +
                "of real thought. State the riddle only. Do not reveal the answer, and do not mention smoking."
        CoachTask.NUDGE ->
            "Write a single notification line under 20 words that pulls attention away from an approaching " +
                "craving and invites the person to reply in the chat. Be specific to their situation. " +
                "No greeting, no sign-off, no quotation marks."
        CoachTask.INSIGHT ->
            "Point out one pattern in their situation and name one small experiment for today. " +
                "At most three sentences."
        CoachTask.CHECKUP ->
            "Write one unprompted check-in of under 25 words for a notification. Make it land: either " +
                "genuinely funny, or a curious thought that snags attention. Vary the angle each time so it " +
                "never feels like a template. Do not nag, do not mention quitting statistics, and do not use " +
                "quotation marks or a sign-off."
    }

    private fun kickoff(task: CoachTask): String = when (task) {
        CoachTask.RIDDLE -> "Give me a riddle to chew on."
        CoachTask.NUDGE -> "Write my nudge."
        CoachTask.INSIGHT -> "What do you notice?"
        CoachTask.CHECKUP -> "Check in on me."
        CoachTask.CHAT -> ""
    }

    private fun facts(context: CoachContext): String = buildString {
        appendLine("Their situation right now (all figures are from their own device):")
        appendLine("- Cigarettes logged today: ${context.countToday} of a ${context.ceiling} ceiling")
        context.minutesSinceLastCigarette?.let { appendLine("- Minutes since last cigarette: $it") }
        context.minutesUntilNextWindow?.let { appendLine("- Minutes until their next planned window: $it") }
        appendLine("- Smoke-free hours in the current run: ${context.smokeFreeHours}")
        if (context.zeroDayStreak > 0) appendLine("- Consecutive zero-cigarette days: ${context.zeroDayStreak}")
        appendLine("- Cigarettes avoided against baseline: ${context.cigarettesAvoided}")
        if (context.moneySaved > 0) {
            appendLine("- Money saved: ${"%.2f".format(context.moneySaved)} ${context.currencyCode}")
        }
        if (context.topTriggers.isNotEmpty()) {
            appendLine("- Triggers they report most: ${context.topTriggers.joinToString(", ")}")
        }
        if (context.personalReason.isNotBlank()) {
            appendLine("- Their own stated reason to quit: \"${context.personalReason.take(300)}\"")
        }
        append("- Preferred coaching tone: ${context.tone.lowercase()}")
    }

    private const val MAX_HISTORY = 12
}
