package com.pace.reduction.core.coach

import com.pace.reduction.core.network.OllamaClient
import com.pace.reduction.core.network.OllamaMessage
import com.pace.reduction.data.repository.PaceRepository
import com.pace.reduction.domain.CoachPrompt
import com.pace.reduction.domain.CoachTask
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

    /** Streams a reply to [prompt], grounded in the local numbers. */
    suspend fun chatStream(prompt: String): Flow<String> {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) return emptyFlow()
        val history = repository.coachMessages.first()
            .takeLast(MAX_HISTORY)
            .map { OllamaMessage(if (it.isUser) "user" else "assistant", it.content) }
        val messages = CoachPrompt.messages(
            task = CoachTask.CHAT,
            context = repository.coachContext(),
            history = history + OllamaMessage("user", prompt),
        )
        return client.chatStream(settings.apiKey, settings.model, messages)
    }

    suspend fun riddle(): String = oneShot(CoachTask.RIDDLE)

    suspend fun nudge(): String = oneShot(CoachTask.NUDGE)

    suspend fun insight(): String = oneShot(CoachTask.INSIGHT)

    /** Confirms the key works and returns the models it can reach. */
    suspend fun verify(apiKey: String): List<String> = client.listModels(apiKey.trim())

    private suspend fun oneShot(task: CoachTask): String {
        val settings = repository.aiSettings.first()
        if (!settings.isReady) return ""
        return client.chat(
            apiKey = settings.apiKey,
            model = settings.model,
            messages = CoachPrompt.messages(task, repository.coachContext(), emptyList()),
        )
    }

    private companion object {
        const val MAX_HISTORY = 12
    }
}
