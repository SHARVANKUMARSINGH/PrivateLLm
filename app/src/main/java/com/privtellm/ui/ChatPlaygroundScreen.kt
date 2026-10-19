package com.privtellm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.hub.ModelItem
import com.privtellm.nativebridge.GenerationChunk
import com.privtellm.nativebridge.NativeLib
import com.privtellm.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class UiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "assistant"
    val content: String,
    val isStreaming: Boolean = false,
    val tps: Double = 0.0,
    val tokenCount: Int = 0
)

@Composable
fun ChatPlaygroundScreen(
    loadedModel: ModelItem?,
    modifier: Modifier = Modifier
) {
    var inputPrompt by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<UiChatMessage>() }
    var isGenerating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header Info Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ON-DEVICE INFERENCE PLAYGROUND",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = loadedModel?.name ?: "No Model Loaded",
                        color = if (loadedModel != null) AccentGreen else AccentAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (loadedModel != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${loadedModel.quantization} • ${loadedModel.parametersBillion}B",
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "💬 Local Neural Engine",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (loadedModel != null)
                                    "Type a prompt below to run inference 100% locally on your ARM64 NPU/GPU/CPU."
                                else
                                    "Please download and load a model from the Model Store tab first.",
                                color = TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }
        }

        // Input & Controls Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = if (loadedModel != null) "Send prompt to ${loadedModel.name}..." else "Load a model first...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    enabled = !isGenerating && loadedModel != null
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isGenerating) {
                    IconButton(
                        onClick = { NativeLib.stopGeneration() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(AccentRed, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val promptToSend = inputPrompt.trim()
                            if (promptToSend.isNotEmpty() && loadedModel != null) {
                                inputPrompt = ""
                                messages.add(UiChatMessage(sender = "user", content = promptToSend))

                                val assistantMsgIndex = messages.size
                                messages.add(
                                    UiChatMessage(
                                        sender = "assistant",
                                        content = "",
                                        isStreaming = true
                                    )
                                )

                                isGenerating = true

                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                    val stream = NativeLib.generateStream(promptToSend)
                                    val fullResponse = StringBuilder()

                                    stream.collect { chunk: GenerationChunk ->
                                        if (chunk.token.isNotEmpty()) {
                                            fullResponse.append(chunk.token)
                                            messages[assistantMsgIndex] = messages[assistantMsgIndex].copy(
                                                content = fullResponse.toString(),
                                                tokenCount = fullResponse.length / 4
                                            )
                                        }

                                        if (chunk.isFinished) {
                                            messages[assistantMsgIndex] = messages[assistantMsgIndex].copy(
                                                content = if (chunk.error != null)
                                                    "⚠️ Error: ${chunk.error}"
                                                else
                                                    fullResponse.toString(),
                                                isStreaming = false,
                                                tps = chunk.tokensPerSecond,
                                                tokenCount = chunk.completionTokens
                                            )
                                        }
                                    }
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = inputPrompt.isNotBlank() && loadedModel != null,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (inputPrompt.isNotBlank() && loadedModel != null) AccentGreen else DarkSurfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputPrompt.isNotBlank() && loadedModel != null) DarkBackground else TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: UiChatMessage) {
    val isUser = message.sender == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = if (isUser) DarkSurfaceVariant else DarkSurface,
            border = if (!isUser) CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(BorderColor)
            ) else null
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "PrivteLLM Engine",
                    color = if (isUser) AccentBlue else AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content.ifEmpty { if (message.isStreaming) "Thinking..." else "" },
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                if (!isUser && !message.isStreaming && message.tps > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚡ ${String.format("%.1f", message.tps)} tok/s • ${message.tokenCount} tokens",
                        color = AccentGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
