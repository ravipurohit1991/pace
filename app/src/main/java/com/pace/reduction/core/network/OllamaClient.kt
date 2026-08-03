package com.pace.reduction.core.network

import com.pace.reduction.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class OllamaMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean,
    val think: Boolean = false,
    val options: ChatOptions = ChatOptions(),
)

@Serializable
private data class ChatOptions(
    val temperature: Double = 0.85,
    /**
     * Generous on purpose. Reasoning models spend this budget on their internal trace before
     * emitting any content, so a tight cap returns an empty message with done_reason "length" —
     * silently, with no error to surface. The visible reply is still bounded by MAX_REPLY_CHARS.
     */
    val num_predict: Int = 1_600,
)

@Serializable
private data class ChatChunkMessage(val role: String = "assistant", val content: String = "")

@Serializable
private data class ChatChunk(
    val message: ChatChunkMessage? = null,
    val done: Boolean = false,
    @SerialName("done_reason") val doneReason: String? = null,
    val error: String? = null,
)

@Serializable
private data class TagEntry(val name: String)

@Serializable
private data class TagsResponse(val models: List<TagEntry> = emptyList())

/**
 * Minimal client for Ollama Cloud (https://ollama.com). The user supplies their own API key;
 * nothing is sent anywhere until they enable the coach and paste a key.
 */
class OllamaClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Streams assistant text token by token. Reasoning traces are dropped, only content is emitted. */
    fun chatStream(
        apiKey: String,
        model: String,
        messages: List<OllamaMessage>,
    ): Flow<String> = flow {
        val request = buildRequest(
            apiKey = apiKey,
            path = "api/chat",
            body = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(model = model, messages = messages, stream = true),
            ),
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(describeFailure(response.code))
            val source = response.body.source()
            var emitted = 0
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line()?.trim().orEmpty()
                if (line.isEmpty()) continue
                val chunk = runCatching { json.decodeFromString(ChatChunk.serializer(), line) }.getOrNull() ?: continue
                chunk.error?.let { error(it.take(200)) }
                val text = chunk.message?.content.orEmpty()
                if (text.isNotEmpty() && emitted < MAX_REPLY_CHARS) {
                    emitted += text.length
                    emit(text)
                }
                if (chunk.done || emitted >= MAX_REPLY_CHARS) break
            }
        }
    }.flowOn(Dispatchers.IO)

    /** One-shot completion used for notification copy, riddles and daily insights. */
    suspend fun chat(apiKey: String, model: String, messages: List<OllamaMessage>): String =
        withContext(Dispatchers.IO) {
            val request = buildRequest(
                apiKey = apiKey,
                path = "api/chat",
                body = json.encodeToString(
                    ChatRequest.serializer(),
                    ChatRequest(model = model, messages = messages, stream = false),
                ),
            )
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(describeFailure(response.code))
                val body = response.body.string().take(MAX_RESPONSE_CHARS)
                val chunk = json.decodeFromString(ChatChunk.serializer(), body)
                chunk.error?.let { error(it.take(200)) }
                val content = chunk.message?.content.orEmpty().trim()
                // An empty reply that stopped on "length" means the budget went to reasoning.
                // Surface it rather than letting the caller show a blank card.
                if (content.isEmpty() && chunk.doneReason == "length") {
                    error("The model ran out of room before answering")
                }
                content.take(MAX_REPLY_CHARS)
            }
        }

    /** Verifies the key and returns the cloud models it can reach. */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/tags")
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(describeFailure(response.code))
            val body = response.body.string().take(MAX_RESPONSE_CHARS)
            json.decodeFromString(TagsResponse.serializer(), body).models
                .map(TagEntry::name)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
    }

    private fun buildRequest(apiKey: String, path: String, body: String): Request = Request.Builder()
        .url("$BASE_URL/$path")
        .header("Authorization", "Bearer $apiKey")
        .header("User-Agent", USER_AGENT)
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun describeFailure(code: Int): String = when (code) {
        401, 403 -> "Ollama rejected the API key"
        404 -> "That model is not available on your Ollama account"
        429 -> "Ollama rate limit reached, try again shortly"
        else -> "Ollama request failed ($code)"
    }

    companion object {
        const val BASE_URL = "https://ollama.com"
        const val DEFAULT_MODEL = "gpt-oss:120b"
        val SUGGESTED_MODELS = listOf("gpt-oss:120b", "gpt-oss:20b", "qwen3.5:397b", "deepseek-v4-flash")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val USER_AGENT = "Pace/${BuildConfig.VERSION_NAME}"
        private const val MAX_RESPONSE_CHARS = 256 * 1024
        private const val MAX_REPLY_CHARS = 4_000
    }
}
