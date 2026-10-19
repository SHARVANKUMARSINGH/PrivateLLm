package com.privtellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.hub.ModelItem
import com.privtellm.nativebridge.GenerationChunk
import com.privtellm.nativebridge.NativeLib
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

data class PlaygroundMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "assistant"
    val content: String,
    val imageUri: Uri? = null,
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
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }

    // TTS Voice Engine
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        ttsEngine = tts
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Image Picker Launcher for Vision Models
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        attachedImageUri = uri
        if (uri != null) {
            Toast.makeText(context, "Image attached for Vision processing", Toast.LENGTH_SHORT).show()
        }
    }

    // Inference Hyperparameters
    var systemPrompt by remember { mutableStateOf("You are a helpful, concise AI assistant running 100% locally on an Android device.") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var topP by remember { mutableFloatStateOf(0.9f) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val promptSuggestions = listOf(
        "Explain ARM NEON vectorization",
        "Write a Kotlin Coroutine Flow example",
        "How does 4-bit GGUF quantization work?",
        "Why is on-device private AI important?"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Top Model Status Bar (Clean Google Surface)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (loadedModel != null) colorScheme.primary else colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = loadedModel?.name ?: "No Model Loaded",
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (loadedModel != null)
                                "${loadedModel.quantization} • ${loadedModel.parametersBillion}B params • temp: ${String.format("%.1f", temperature)}"
                            else
                                "Load a model in Models tab to chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Parameters",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { messages.clear() },
                        enabled = messages.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = if (messages.isNotEmpty()) colorScheme.onSurfaceVariant else colorScheme.outlineVariant,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Private Neural Chat",
                            style = MaterialTheme.typography.titleLarge,
                            color = colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (loadedModel != null)
                                "'${loadedModel.name}' is loaded in RAM. Ready for private, offline conversation."
                            else
                                "Go to the 'Models' tab and load a quantized model into RAM to begin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                PlaygroundBubble(
                    message = message,
                    context = context,
                    onCopy = {
                        val clip = ClipData.newPlainText("AI Response", message.content)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onSpeak = { text ->
                        ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PRIVTE_SPEAK")
                    }
                )
            }
        }

        // Quick Prompt Suggestions Row (Google Gemini Style Pills)
        if (loadedModel != null && !isGenerating && messages.isEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(promptSuggestions) { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            inputPrompt = suggestion
                        }
                    ) {
                        Text(
                            text = suggestion,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Floating Capsule Input Bar (Google Messages / Gemini Style)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(28.dp),
            color = colorScheme.surfaceVariant,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Attached Image Thumbnail Preview Bar
                AnimatedVisibility(visible = attachedImageUri != null) {
                    attachedImageUri?.let { uri ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val bitmap = remember(uri) {
                                    try {
                                        context.contentResolver.openInputStream(uri)?.use {
                                            BitmapFactory.decodeStream(it)
                                        }
                                    } catch (_: Exception) { null }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Attached Image",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Vision Input Attached",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Image will be sent with prompt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { attachedImageUri = null },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Image",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vision Image Attachment (+) Button
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach Image",
                            tint = if (attachedImageUri != null) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    TextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = {
                            Text(
                                text = if (loadedModel != null)
                                    if (attachedImageUri != null) "Ask about this image..." else "Message ${loadedModel.name}..."
                                else
                                    "Load a model in Models tab first...",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface
                        ),
                        maxLines = 4,
                        enabled = !isGenerating && loadedModel != null
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isGenerating) {
                        FilledIconButton(
                            onClick = { NativeLib.stopGenerationSafe() },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Generation",
                                tint = colorScheme.onError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        val canSend = (inputPrompt.isNotBlank() || attachedImageUri != null) && loadedModel != null
                        FilledIconButton(
                            onClick = {
                                val userText = inputPrompt.trim()
                                val currentImage = attachedImageUri
                                if ((userText.isNotEmpty() || currentImage != null) && loadedModel != null) {
                                    inputPrompt = ""
                                    attachedImageUri = null
                                    messages.add(
                                        PlaygroundMessage(
                                            sender = "user",
                                            content = userText.ifEmpty { "Describe this image." },
                                            imageUri = currentImage
                                        )
                                    )

                                    val assistantMsg = PlaygroundMessage(
                                        sender = "assistant",
                                        content = "",
                                        isStreaming = true
                                    )
                                    messages.add(assistantMsg)
                                    isGenerating = true

                                    coroutineScope.launch {
                                        listState.animateScrollToItem(messages.size - 1)
                                        var accumulated = ""
                                        var lastTps = 0.0
                                        var tokens = 0

                                        try {
                                            NativeLib.generateSafeFlow(
                                                prompt = userText.ifEmpty { "Describe this image." },
                                                maxTokens = 512,
                                                temperature = temperature,
                                                topP = topP
                                            ).collect { chunk: GenerationChunk ->
                                                accumulated += chunk.token
                                                lastTps = chunk.tokensPerSecond
                                                tokens = chunk.tokenCount

                                                val index = messages.indexOfFirst { it.id == assistantMsg.id }
                                                if (index != -1) {
                                                    messages[index] = assistantMsg.copy(
                                                        content = accumulated,
                                                        isStreaming = !chunk.isFinished,
                                                        tps = lastTps,
                                                        tokenCount = tokens
                                                    )
                                                }
                                                listState.animateScrollToItem(messages.size - 1)
                                            }
                                        } catch (t: Throwable) {
                                            val index = messages.indexOfFirst { it.id == assistantMsg.id }
                                            if (index != -1) {
                                                messages[index] = assistantMsg.copy(
                                                    content = "Generation error: ${t.localizedMessage ?: "Unknown error"}",
                                                    isStreaming = false
                                                )
                                            }
                                        } finally {
                                            isGenerating = false
                                            val index = messages.indexOfFirst { it.id == assistantMsg.id }
                                            if (index != -1) {
                                                messages[index] = messages[index].copy(isStreaming = false)
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (canSend) colorScheme.primary else colorScheme.surfaceVariant,
                                contentColor = if (canSend) colorScheme.onPrimary else colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send Prompt",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Parameters Modal Dialog (Material 3 Google Style)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = {
                Icon(Icons.Default.Tune, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Inference Parameters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text("System Prompt", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
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
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Temperature", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                            Text(String.format("%.2f", temperature), style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = colorScheme.primary, activeTrackColor = colorScheme.primary)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Top-P", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                            Text(String.format("%.2f", topP), style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = topP,
                            onValueChange = { topP = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = colorScheme.primary, activeTrackColor = colorScheme.primary)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Apply", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun PlaygroundBubble(
    message: PlaygroundMessage,
    context: Context,
    onCopy: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isUser = message.sender == "user"

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
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            color = if (isUser) colorScheme.primaryContainer else colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Image preview if user sent an image
                message.imageUri?.let { uri ->
                    val bmp = remember(uri) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        } catch (_: Exception) { null }
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Header inside assistant bubble: model badge & action buttons
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PrivteLLM",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        if (message.content.isNotEmpty() && !message.isStreaming) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Speak Text",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onSpeak(message.content) }
                                )

                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onCopy() }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Message Text with cursor
                Row {
                    Text(
                        text = message.content.ifEmpty { if (message.isStreaming) "Thinking..." else "" },
                        color = if (isUser) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                    if (message.isStreaming) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(18.dp)
                                .offset(x = 3.dp, y = 2.dp)
                                .background(colorScheme.primary.copy(alpha = cursorAlpha))
                        )
                    }
                }

                // Telemetry Footer
                if (!isUser && !message.isStreaming && message.tps > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${String.format("%.1f", message.tps)} tok/s",
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${message.tokenCount} tokens",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
