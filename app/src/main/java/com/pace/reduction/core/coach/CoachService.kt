package com.pace.reduction.core.coach

import com.pace.reduction.core.network.OllamaClient
import com.pace.reduction.core.network.OllamaMessage
import com.pace.reduction.core.network.OllamaVisionTurn
import com.pace.reduction.data.repository.PaceRepository
import com.pace.reduction.domain.ArcadeOffline
import com.pace.reduction.domain.ArcadeParser
import com.pace.reduction.domain.Autopilot
import com.pace.reduction.domain.AutopilotPick
import com.pace.reduction.domain.CoachActions
import com.pace.reduction.domain.CoachBeat
import com.pace.reduction.domain.CoachPrompt
import com.pace.reduction.domain.CoachTask
import com.pace.reduction.domain.CoachImageMemory
import com.pace.reduction.domain.EmojiPuzzle
import com.pace.reduction.domain.IfThenPlan
import com.pace.reduction.domain.Playbook
import com.pace.reduction.domain.StoryBeat
import com.pace.reduction.domain.ToolStat
import com.pace.reduction.domain.TriviaQuestion
import com.pace.reduction.domain.model.AiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first

/**
 * Bridges the encrypted key + local stats to Ollama Cloud. Every entry point is a no-op
 * unless the user turned the coach on and saved a key.
 */
class CoachService(
    private val repository: PaceRepository,
    private val client: OllamaClient = OllamaClient(),
) {
    suspend fun isReady(): Boolean = repository.aiSettings.first().isReady

    /**
     * Streams the next reply for the stored conversation, grounded in the local numbers when the
     * user allows it. The caller persists the user's message first; this replays the whole recent
     * exchange so the model always answers with full context rather than a bare prompt.
     */
    suspend fun chatStream(): Flow<String> {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) return emptyFlow()
        val storedHistory = repository.coachMessages.first().takeLast(MAX_HISTORY)
        val history = storedHistory
            .map {
                OllamaMessage(
                    if (it.isUser) "user" else "assistant",
                    if (it.isUser) CoachImageMemory.modelContent(it.content) else it.content,
                )
            }
        if (history.isEmpty()) return emptyFlow()
        val messages = CoachPrompt.messages(
            task = CoachTask.CHAT,
            context = groundingFor(settings),
            history = history,
            persona = settings.systemPrompt,
            allowActions = true,
        )
        return client.chatStream(settings.apiKey, settings.model, messages)
    }

    /**
     * Handles the current image and its conversational answer in one request with one model. The
     * full recent chat is included, but only the current user message receives the image bytes.
     */
    suspend fun imageReply(imageBase64: String): OllamaVisionTurn {
        val settings = repository.aiSettings.first()
        if (!settings.isVisionReady) error("Choose a multimodal Ollama model in Settings")
        val storedHistory = repository.coachMessages.first().takeLast(MAX_HISTORY)
        val history = storedHistory
            .map {
                OllamaMessage(
                    role = if (it.isUser) "user" else "assistant",
                    content = if (it.isUser) CoachImageMemory.modelContent(it.content) else it.content,
                )
            }
            .toMutableList()
        val currentUser = history.indexOfLast { it.role == "user" }
        if (currentUser < 0) error("The photo message could not be prepared")
        history[currentUser] = history[currentUser].copy(
            content = CoachImageMemory.displayContent(storedHistory[currentUser].content) + IMAGE_TURN_INSTRUCTION,
            images = listOf(imageBase64),
        )
        return client.visionChat(
            apiKey = settings.apiKey,
            // One model owns both visual understanding and the reply for this turn.
            model = settings.model,
            messages = CoachPrompt.messages(
                task = CoachTask.CHAT,
                context = groundingFor(settings),
                history = history,
                persona = settings.systemPrompt,
                extraSystemInstruction = buildString {
                    append(
                        settings.imageSystemPrompt.ifBlank { CoachPrompt.DEFAULT_IMAGE_INSTRUCTION },
                    )
                    append(STRUCTURED_IMAGE_OUTPUT_INSTRUCTION)
                },
            ),
        )
    }

    /** Speech-only conversation. The caller owns its short in-memory history. */
    suspend fun voiceReply(history: List<OllamaMessage>): String {
        val settings = repository.aiSettings.first()
        if (!settings.isReady || history.isEmpty()) return ""
        val reply = client.chat(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(
                task = CoachTask.CHAT,
                context = groundingFor(settings),
                history = history.takeLast(MAX_HISTORY),
                persona = settings.systemPrompt,
            ),
        )
        // Spoken aloud, so a stray tag copied from the text chat must never be read out.
        return CoachActions.strip(reply)
    }

    suspend fun riddle(): String = oneShot(CoachTask.RIDDLE)

    /** A concrete, oddly specific thing to do right now — replaces the generic countdown. */
    suspend fun rescuePlan(): String = oneShot(CoachTask.RESCUE)

    suspend fun quote(): String = oneShot(CoachTask.QUOTE)

    suspend fun nudge(): String = oneShot(CoachTask.NUDGE)

    suspend fun insight(): String = oneShot(CoachTask.INSIGHT)

    /** Unprompted funny or curious line used by the periodic check-in notification. */
    suspend fun checkup(): String = oneShot(CoachTask.CHECKUP)

    /**
     * One beat of the coach's own daily agenda.
     *
     * [suggestedMove] is the session the app is about to put in front of them, passed in so the
     * written line and the button underneath it name the same thing — a message inviting someone
     * on a walk above a button that starts a yoga flow is worse than no message.
     */
    suspend fun agendaMessage(beat: CoachBeat, suggestedMove: String? = null): String {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) return ""
        val task = when (beat) {
            CoachBeat.MORNING_PLAN -> CoachTask.MORNING_PLAN
            CoachBeat.MOVE_INVITE -> CoachTask.MOVE_INVITE
            CoachBeat.EVENING_REFLECT -> CoachTask.EVENING_REFLECT
        }
        return client.chat(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(
                task = task,
                context = if (settings.includeStats) repository.coachContext(suggestedMove) else null,
                history = emptyList(),
                persona = settings.systemPrompt,
            ),
        )
    }

    /**
     * Five fresh trivia questions on [topic]. Not grounded in their figures: a quiz about your own
     * numbers is the opposite of a distraction.
     */
    suspend fun triviaRound(topic: String): List<TriviaQuestion> {
        val raw = arcadeJson(
            task = CoachTask.TRIVIA,
            kickoff = if (topic.isBlank() || topic == ArcadeOffline.triviaTopics.first()) {
                "Five questions, surprise me with a mix of topics."
            } else {
                "Five questions about ${topic.take(40)}."
            },
            schema = ArcadeParser.Schemas.trivia,
        )
        return ArcadeParser.trivia(raw).also { if (it.size < 3) error("The quiz came back garbled, try again") }
    }

    suspend fun emojiRound(): List<EmojiPuzzle> {
        val raw = arcadeJson(CoachTask.EMOJI, "Give me five emoji puzzles.", ArcadeParser.Schemas.emoji)
        return ArcadeParser.emoji(raw).also { if (it.size < 3) error("The puzzles came back garbled, try again") }
    }

    /**
     * The next beat of a story. [path] is every scene so far paired with the choice made after it,
     * replayed as a conversation so the model keeps its own plot straight.
     */
    suspend fun storyBeat(genre: String, path: List<Pair<String, String>>): StoryBeat {
        val history = path.flatMap { (scene, choice) ->
            listOf(OllamaMessage("assistant", scene), OllamaMessage("user", "I choose: $choice"))
        }
        val beatNumber = path.size + 1
        val kickoff = when {
            path.isEmpty() -> "Start a new ${genre.take(40).lowercase()} story. This is beat 1 of ${ArcadeOffline.STORY_BEATS}."
            beatNumber >= ArcadeOffline.STORY_BEATS ->
                "Continue from my choice. This is the final beat: land the ending now, no choices."
            else -> "Continue from my choice. This is beat $beatNumber of ${ArcadeOffline.STORY_BEATS}."
        }
        val raw = arcadeJson(CoachTask.STORY, kickoff, ArcadeParser.Schemas.story, history)
        return ArcadeParser.story(raw) ?: error("The story lost its thread, try again")
    }

    /**
     * Lets the model choose the tool. It sees their grounded figures (when shared), the strength
     * they reported, what they used last and what has measurably helped, and must answer with an
     * id the app can open — anything else is rejected and the caller falls back to the device.
     */
    suspend fun autopilot(strength: Int?, recent: List<String>, stats: List<ToolStat>): AutopilotPick? {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) return null
        val kickoff = buildString {
            append("Pick something for me to do right now.")
            strength?.let { append(" Right now it is a $it out of 5 for me.") }
            if (recent.isNotEmpty()) append(" I last used: ${recent.joinToString(", ")}.")
            val evidence = stats.filter { it.rated > 0 }.take(4)
            if (evidence.isNotEmpty()) {
                append(" What has helped before (average fall on a 1 to 5 scale): ")
                append(evidence.joinToString(", ") { "${it.toolId} ${"%.1f".format(it.averageDrop ?: 0.0)}" })
                append(".")
            }
        }
        val raw = client.chatJson(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(
                task = CoachTask.AUTOPILOT,
                context = groundingFor(settings),
                history = emptyList(),
                persona = settings.systemPrompt,
                extraSystemInstruction = "",
                kickoffOverride = kickoff,
            ),
            schema = Autopilot.schema,
            temperature = 0.7,
        )
        return Autopilot.parse(raw)
    }

    /** Three if-then plans drafted from their own patterns, minus the ones they already keep. */
    suspend fun draftPlaybook(existing: List<IfThenPlan>): List<IfThenPlan> {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) return emptyList()
        val kickoff = buildString {
            append("Draft me three if-then plans.")
            if (existing.isNotEmpty()) {
                append(" I already have: ")
                append(existing.joinToString("; ") { "if ${it.cue}, then ${it.action}" })
                append(".")
            }
        }
        val raw = client.chatJson(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(
                task = CoachTask.PLAYBOOK,
                // The plans are only as good as the cues, and the cues live in their figures.
                context = groundingFor(settings),
                history = emptyList(),
                persona = settings.systemPrompt,
                kickoffOverride = kickoff,
            ),
            schema = Playbook.schema,
            temperature = 0.8,
        )
        return Playbook.parseSuggestions(raw).also { if (it.isEmpty()) error("No usable plans came back, try again") }
    }

    private suspend fun arcadeJson(
        task: CoachTask,
        kickoff: String,
        schema: kotlinx.serialization.json.JsonObject,
        history: List<OllamaMessage> = emptyList(),
    ): String {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) error("Set up the AI coach in Settings to play this")
        return client.chatJson(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(
                task = task,
                context = null,
                history = history,
                persona = settings.systemPrompt,
                kickoffOverride = kickoff,
            ),
            schema = schema,
        )
    }

    /** Confirms the key works and returns the models it can reach. */
    suspend fun verify(apiKey: String): List<String> = client.listModels(apiKey.trim())

    private suspend fun oneShot(task: CoachTask): String {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) return ""
        return client.chat(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(
                task = task,
                context = groundingFor(settings),
                history = emptyList(),
                persona = settings.systemPrompt,
            ),
        )
    }

    private suspend fun groundingFor(settings: AiSettings) =
        if (settings.includeStats) repository.coachContext() else null

    private companion object {
        /** Turns of conversation replayed to the model on every message. */
        const val MAX_HISTORY = 20
        const val IMAGE_TURN_INSTRUCTION =
            "\n\nStudy the attached photo. Keep image_context factual and useful as memory for " +
                "later turns. Put the natural response to my message in reply."
        const val STRUCTURED_IMAGE_OUTPUT_INSTRUCTION =
            "\n\nInternal output contract for this photo turn: return valid JSON only, with exactly " +
                "image_context and reply. The reply field must contain the natural plain-text answer."
    }
}
