package com.pace.reduction.core.coach

import com.pace.reduction.core.network.OllamaClient
import com.pace.reduction.core.network.OllamaMessage
import com.pace.reduction.core.network.OllamaVisionTurn
import com.pace.reduction.data.repository.PaceRepository
import com.pace.reduction.domain.CoachPrompt
import com.pace.reduction.domain.CoachTask
import com.pace.reduction.domain.CoachImageMemory
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
        return client.chat(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(
                task = CoachTask.CHAT,
                context = groundingFor(settings),
                history = history.takeLast(MAX_HISTORY),
                persona = settings.systemPrompt,
            ),
        )
    }

    suspend fun riddle(): String = oneShot(CoachTask.RIDDLE)

    /** A concrete, oddly specific thing to do right now — replaces the generic countdown. */
    suspend fun rescuePlan(): String = oneShot(CoachTask.RESCUE)

    suspend fun quote(): String = oneShot(CoachTask.QUOTE)

    suspend fun nudge(): String = oneShot(CoachTask.NUDGE)

    suspend fun insight(): String = oneShot(CoachTask.INSIGHT)

    /** Unprompted funny or curious line used by the periodic check-in notification. */
    suspend fun checkup(): String = oneShot(CoachTask.CHECKUP)

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
