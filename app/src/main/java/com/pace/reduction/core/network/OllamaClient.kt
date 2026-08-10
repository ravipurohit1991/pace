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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String,
    /** Ollama's REST API expects raw image bytes encoded as base64 on a user message. */
    val images: List<String>? = null,
)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean,
    val think: Boolean = false,
    val options: ChatOptions = ChatOptions(),
    val format: JsonObject? = null,
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

@Serializable
private data class ShowRequest(val model: String, val verbose: Boolean = false)

@Serializable
private data class ShowResponse(val capabilities: List<String> = emptyList())

@Serializable
private data class ErrorResponse(val error: String = "")

@Serializable
private data class VisionTurnWire(
    @SerialName("image_context") val imageContext: String = "",
    val reply: String = "",
)

data class OllamaVisionTurn(val imageContext: String, val reply: String)

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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

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
            if (!response.isSuccessful) error(describeFailure(response))
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
                if (!response.isSuccessful) error(describeFailure(response))
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

    /**
     * Verifies the key and returns only models that can both complete text and inspect images.
     * `/api/tags` does not expose capabilities, so each live tag is checked with `/api/show`.
     */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/tags")
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(describeFailure(response))
            val body = response.body.string().take(MAX_RESPONSE_CHARS)
            json.decodeFromString(TagsResponse.serializer(), body).models
                .map(TagEntry::name)
                .filter { it.isNotBlank() }
                .distinct()
                .filter { model -> modelCapabilities(apiKey, model).containsAll(REQUIRED_CAPABILITIES) }
                .map(::preferredCloudAlias)
                .sortedWith(compareByDescending<String> { it == DEFAULT_MODEL }.thenBy { it })
        }
    }

    /** One multimodal request returns both the user-facing answer and future text-only memory. */
    suspend fun visionChat(
        apiKey: String,
        model: String,
        messages: List<OllamaMessage>,
    ): OllamaVisionTurn = withContext(Dispatchers.IO) {
        val request = buildRequest(
            apiKey = apiKey,
            path = "api/chat",
            body = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(
                    model = model,
                    messages = messages,
                    stream = false,
                    format = VISION_TURN_SCHEMA,
                ),
            ),
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(describeFailure(response))
            val body = response.body.string().take(MAX_RESPONSE_CHARS)
            val chunk = json.decodeFromString(ChatChunk.serializer(), body)
            chunk.error?.let { error(it.take(MAX_ERROR_CHARS)) }
            val raw = chunk.message?.content.orEmpty().trim()
            val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching { json.decodeFromString(VisionTurnWire.serializer(), cleaned) }.getOrNull()
            if (parsed != null && parsed.reply.isNotBlank()) {
                OllamaVisionTurn(
                    imageContext = parsed.imageContext.trim().take(MAX_IMAGE_CONTEXT_CHARS),
                    reply = parsed.reply.trim().take(MAX_REPLY_CHARS),
                )
            } else {
                if (raw.isBlank()) error("The vision model returned an empty response")
                // A non-conforming model response is still useful as the reply and as coarse memory.
                OllamaVisionTurn(raw.take(MAX_IMAGE_CONTEXT_CHARS), raw.take(MAX_REPLY_CHARS))
            }
        }
    }

    private fun modelCapabilities(apiKey: String, model: String): Set<String> {
        val request = buildRequest(
            apiKey = apiKey,
            path = "api/show",
            body = json.encodeToString(ShowRequest.serializer(), ShowRequest(model)),
        )
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptySet()
            val body = response.body.string().take(MAX_RESPONSE_CHARS)
            runCatching { json.decodeFromString(ShowResponse.serializer(), body).capabilities.toSet() }
                .getOrDefault(emptySet())
        }
    }

    private fun buildRequest(apiKey: String, path: String, body: String): Request = Request.Builder()
        .url("$BASE_URL/$path")
        .header("Authorization", "Bearer $apiKey")
        .header("User-Agent", USER_AGENT)
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun describeFailure(response: Response): String {
        val detail = runCatching {
            val body = response.body.string().take(MAX_ERROR_CHARS)
            json.decodeFromString(ErrorResponse.serializer(), body).error.trim()
        }.getOrDefault("")
        return when (response.code) {
            401, 403 -> detail.ifBlank { "Ollama rejected the API key or account access" }
            404 -> detail.ifBlank { "That model is not available on your Ollama account" }
            410 -> detail.ifBlank { "That Ollama model has been retired" }
            429 -> detail.ifBlank { "Ollama rate limit reached, try again shortly" }
            else -> detail.ifBlank { "Ollama request failed (${response.code})" }
        }.take(MAX_ERROR_CHARS)
    }

    /** Direct cloud accepts both names; keep the canonical cloud tag the user sees in the library. */
    private fun preferredCloudAlias(model: String): String = when (model) {
        "qwen3.5:397b" -> "qwen3.5:397b-cloud"
        else -> model
    }

    companion object {
        const val BASE_URL = "https://ollama.com"
        const val DEFAULT_MODEL = "qwen3.5:397b-cloud"
        const val DEFAULT_VISION_MODEL = DEFAULT_MODEL
        val SUGGESTED_TEXT_MODELS = listOf(DEFAULT_MODEL)
        val SUGGESTED_VISION_MODELS = listOf(DEFAULT_MODEL)
        @Deprecated("Use SUGGESTED_TEXT_MODELS", ReplaceWith("SUGGESTED_TEXT_MODELS"))
        val SUGGESTED_MODELS = SUGGESTED_TEXT_MODELS
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val USER_AGENT = "Pace/${BuildConfig.VERSION_NAME}"
        private const val MAX_RESPONSE_CHARS = 256 * 1024
        private const val MAX_REPLY_CHARS = 4_000
        private const val MAX_ERROR_CHARS = 600
        private const val MAX_IMAGE_CONTEXT_CHARS = 900
        private val REQUIRED_CAPABILITIES = setOf("completion", "vision")
        private val VISION_TURN_SCHEMA = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("properties", buildJsonObject {
                put("image_context", buildJsonObject {
                    put("type", "string")
                    put("description", "A factual visual description under 90 words for future chat context")
                })
                put("reply", buildJsonObject {
                    put("type", "string")
                    put("description", "The concise conversational reply to show the user")
                })
            })
            put("required", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("image_context"))
                add(kotlinx.serialization.json.JsonPrimitive("reply"))
            })
        }
    }
}
