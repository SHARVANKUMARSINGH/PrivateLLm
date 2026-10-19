package com.privtellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

data class PlaygroundMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "assistant"
    val content: String,
    val isStreaming: Boolean = false,
    val tps: Double = 0.0,
    val tokenCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPlaygroundScreen(
    loadedModel: ModelItem?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var inputPrompt by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<PlaygroundMessage>() }
    var isGenerating by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Inference Hyperparameters
    var systemPrompt by remember { mutableStateOf("You are a helpful, concise AI assistant running 100% locally on an Android device.") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var topP by remember { mutableFloatStateOf(0.9f) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val promptSuggestions = listOf(
        "⚡ Explain ARM NEON SIMD vectorization",
        "🧩 Write a Kotlin Coroutine Flow example",
        "🧠 How does 4-bit GGUF quantization work?",
        "🔒 Why is on-device private AI important?"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Top Playground Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.surface,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.5f), colorScheme.outline.copy(alpha = 0.5f)))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (loadedModel != null) AccentBlue else StatusWarning)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = loadedModel?.name ?: "No Model Loaded",
                            color = colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (loadedModel != null)
                                "${loadedModel.quantization} • ${loadedModel.parametersBillion}B params • temp: ${String.format("%.1f", temperature)}"
                            else
                                "Load a model in Model Store to chat",
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row {
                    // Hyperparameter Settings Button
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Parameters",
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Clear Chat Button
                    IconButton(
                        onClick = { messages.clear() },
                        enabled = messages.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = if (messages.isNotEmpty()) colorScheme.onSurface.copy(alpha = 0.7f) else colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Messages Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surfaceVariant,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "On-Device Neural Engine",
                            color = colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (loadedModel != null)
                                "Model '${loadedModel.name}' is primed in RAM. Responses stream locally via C++20 NDK."
                            else
                                "Please go to the 'Model Store' tab and load a quantized model into RAM to begin.",
                            color = colorScheme.onBackground.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                PlaygroundBubble(
                    message = message,
                    onCopy = {
                        val clip = ClipData.newPlainText("AI Response", message.content)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Quick Prompt Suggestions Row
        if (loadedModel != null && !isGenerating && messages.isEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(promptSuggestions) { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.surface,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
                        ),
                        modifier = Modifier.clickable {
                            inputPrompt = suggestion
                        }
                    ) {
                        Text(
                            text = suggestion,
                            color = colorScheme.onSurface.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Input & Controls Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = if (loadedModel != null) "Message ${loadedModel.name}..." else "Load a model in Model Store first...",
                            color = colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceVariant,
                        unfocusedContainerColor = colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    enabled = !isGenerating && loadedModel != null
                )

                Spacer(modifier = Modifier.width(10.dp))

                if (isGenerating) {
                    IconButton(
                        onClick = { NativeLib.stopGeneration() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(StatusCritical, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Generation",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val userText = inputPrompt.trim()
                            if (userText.isNotEmpty() && loadedModel != null) {
                                inputPrompt = ""
                                messages.add(PlaygroundMessage(sender = "user", content = userText))

                                val assistantIndex = messages.size
                                messages.add(
                                    PlaygroundMessage(
                                        sender = "assistant",
                                        content = "",
                                        isStreaming = true
                                    )
                                )

                                isGenerating = true

                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                    val formattedPrompt = "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$userText<|im_end|>\n<|im_start|>assistant\n"
                                    val stream = NativeLib.generateStream(formattedPrompt)
                                    val fullResponse = StringBuilder()

                                    stream.collect { chunk: GenerationChunk ->
                                        if (chunk.token.isNotEmpty()) {
                                            fullResponse.append(chunk.token)
                                            messages[assistantIndex] = messages[assistantIndex].copy(
                                                content = fullResponse.toString(),
                                                tokenCount = fullResponse.length / 4
                                            )
                                        }

                                        if (chunk.isFinished) {
                                            messages[assistantIndex] = messages[assistantIndex].copy(
                                                content = if (chunk.error != null)
                                                    "⚠️ ${chunk.error}"
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
                                if (inputPrompt.isNotBlank() && loadedModel != null) AccentBlue else colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Prompt",
                            tint = if (inputPrompt.isNotBlank() && loadedModel != null) Color.White else colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Parameters Modal Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text("Inference Parameters", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text("System Prompt", color = colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = colorScheme.surfaceVariant,
                                unfocusedContainerColor = colorScheme.surfaceVariant,
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Temperature", color = colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                            Text(String.format("%.2f", temperature), color = AccentBlue, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Top-P", color = colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                            Text(String.format("%.2f", topP), color = AccentBlue, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        Slider(
                            value = topP,
                            onValueChange = { topP = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
fun PlaygroundBubble(
    message: PlaygroundMessage,
    onCopy: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isUser = message.sender == "user"

    // Blinking cursor transition for streaming assistant text
    val infiniteTransition = rememberInfiniteTransition(label = "CursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )

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
            color = if (isUser) colorScheme.surfaceVariant else colorScheme.surface,
            border = if (!isUser) CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.5f), colorScheme.outline.copy(alpha = 0.3f)))
            ) else null,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Sender Label & Copy Icon Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "PrivteLLM Engine",
                        color = AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    if (!isUser && message.content.isNotEmpty() && !message.isStreaming) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onCopy() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Message Text with Animated Cursor
                Row {
                    Text(
                        text = message.content.ifEmpty { if (message.isStreaming) "Inferring..." else "" },
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                    if (message.isStreaming) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(16.dp)
                                .offset(x = 2.dp, y = 2.dp)
                                .background(AccentBlue.copy(alpha = cursorAlpha))
                        )
                    }
                }

                // Telemetry Footer for Assistant Responses
                if (!isUser && !message.isStreaming && message.tps > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ ${String.format("%.1f", message.tps)} tok/s",
                                color = AccentBlue,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${message.tokenCount} tokens",
                                color = colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
