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

enum class CoachTask { CHAT, RIDDLE, NUDGE, INSIGHT }

object CoachPrompt {
    private const val PERSONA =
        "You are Pace, a calm quit-smoking coach living inside an Android app. " +
            "You help someone reduce and eventually stop smoking. " +
            "Never shame, never moralise, never celebrate smoking. A delay is always a win, " +
            "and a lapse never means failure. You are not a doctor and do not give medical advice; " +
            "if asked for medical guidance, suggest a clinician. " +
            "Write plain text only: no markdown, no bullet lists, no emoji, no headings."

    fun messages(task: CoachTask, context: CoachContext, history: List<OllamaMessage>): List<OllamaMessage> =
        buildList {
            add(OllamaMessage("system", "$PERSONA\n\n${instruction(task)}\n\n${facts(context)}"))
            addAll(history.takeLast(MAX_HISTORY))
            if (task != CoachTask.CHAT) add(OllamaMessage("user", kickoff(task)))
        }

    private fun instruction(task: CoachTask): String = when (task) {
        CoachTask.CHAT ->
            "Hold a short, warm conversation that carries the person past a craving. " +
                "Reply in at most three sentences. Ask one light question so the conversation keeps going. " +
                "If they sound like they are about to smoke, offer a concrete distraction rather than a lecture."
        CoachTask.RIDDLE ->
            "Give exactly one short riddle or lateral-thinking puzzle that takes about two minutes " +
                "of real thought. State the riddle only. Do not reveal the answer, and do not mention smoking."
        CoachTask.NUDGE ->
            "Write a single notification line under 20 words that pulls attention away from an approaching " +
                "craving and invites the person to reply in the chat. Be specific to their numbers below. " +
                "No greeting, no sign-off, no quotation marks."
        CoachTask.INSIGHT ->
            "Point out one pattern in the numbers below and name one small experiment for today. " +
                "At most three sentences."
    }

    private fun kickoff(task: CoachTask): String = when (task) {
        CoachTask.RIDDLE -> "Give me a riddle to chew on."
        CoachTask.NUDGE -> "Write my nudge."
        CoachTask.INSIGHT -> "What do you notice?"
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
