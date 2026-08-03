package com.pace.reduction.core.coach

import com.pace.reduction.core.network.OllamaClient
import com.pace.reduction.core.network.OllamaMessage
import com.pace.reduction.data.repository.PaceRepository
import com.pace.reduction.domain.CoachPrompt
import com.pace.reduction.domain.CoachTask
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
        val history = repository.coachMessages.first()
            .takeLast(MAX_HISTORY)
            .map { OllamaMessage(if (it.isUser) "user" else "assistant", it.content) }
        if (history.isEmpty()) return emptyFlow()
        val messages = CoachPrompt.messages(
            task = CoachTask.CHAT,
            context = groundingFor(settings),
            history = history,
            persona = settings.systemPrompt,
        )
        return client.chatStream(settings.apiKey, settings.model, messages)
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
    }
}
