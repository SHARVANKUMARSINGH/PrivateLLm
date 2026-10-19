package com.privtellm.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String = "privtellm-default",
    val messages: List<ChatMessage>,
    val temperature: Double? = 0.7,
    @SerialName("top_p") val topP: Double? = 0.9,
    @SerialName("max_tokens") val maxTokens: Int? = 1024,
    val stream: Boolean? = false
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = "stop"
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: ChunkDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatCompletionChunk(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>
)

@Serializable
data class ModelData(
    val id: String,
    @SerialName("object") val objectType: String = "model",
    val created: Long = 1710000000L,
    @SerialName("owned_by") val ownedBy: String = "privtellm-device"
)

@Serializable
data class ModelListResponse(
    @SerialName("object") val objectType: String = "list",
    val data: List<ModelData>
)

@Serializable
data class ApiErrorResponse(
    val error: ApiError
)

@Serializable
data class ApiError(
    val message: String,
    val type: String = "server_error",
    val code: Int = 500
)
