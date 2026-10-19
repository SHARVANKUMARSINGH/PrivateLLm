package com.privtellm

import com.privtellm.server.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OpenAiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testChatCompletionRequestParsing() {
        val requestJson = """
            {
                "model": "smollm-360m-instruct-q4_k_m",
                "messages": [
                    {"role": "system", "content": "You are a helpful assistant."},
                    {"role": "user", "content": "Explain quantum computing briefly."}
                ],
                "temperature": 0.8,
                "top_p": 0.95,
                "max_tokens": 512,
                "stream": true
            }
        """.trimIndent()

        val req = json.decodeFromString<ChatCompletionRequest>(requestJson)
        assertEquals("smollm-360m-instruct-q4_k_m", req.model)
        assertEquals(2, req.messages.size)
        assertEquals("system", req.messages[0].role)
        assertEquals("Explain quantum computing briefly.", req.messages[1].content)
        assertEquals(0.8, req.temperature!!, 0.001)
        assertEquals(0.95, req.topP!!, 0.001)
        assertEquals(512, req.maxTokens)
        assertTrue(req.stream == true)
    }

    @Test
    fun testChatCompletionChunkSerialization() {
        val chunk = ChatCompletionChunk(
            id = "chatcmpl-test1234",
            created = 1718000000L,
            model = "llama-3.2-1b-instruct-q4_k_m",
            choices = listOf(
                ChunkChoice(
                    index = 0,
                    delta = ChunkDelta(content = "Hello")
                )
            )
        )

        val serialized = json.encodeToString(chunk)
        assertTrue(serialized.contains("chat.completion.chunk"))
        assertTrue(serialized.contains("Hello"))
        assertTrue(serialized.contains("chatcmpl-test1234"))
    }

    @Test
    fun testModelListResponseSerialization() {
        val listResponse = ModelListResponse(
            data = listOf(
                ModelData(id = "smollm-360m-instruct-q4_k_m"),
                ModelData(id = "llama-3.2-1b-instruct-q4_k_m")
            )
        )

        val serialized = json.encodeToString(listResponse)
        assertTrue(serialized.contains("smollm-360m-instruct-q4_k_m"))
        assertTrue(serialized.contains("llama-3.2-1b-instruct-q4_k_m"))
        assertTrue(serialized.contains("\"object\":\"list\""))
    }
}
