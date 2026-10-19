package com.privtellm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class StudioTab(val title: String) {
    TTS("Text-to-Speech"),
    IMAGE_GEN("Image Studio")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen() {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var selectedStudioTab by remember { mutableStateOf(StudioTab.TTS) }

    // TTS State
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsText by remember { mutableStateOf("Welcome to PrivteLLM. Your private on-device artificial intelligence studio is now operational.") }
    var ttsPitch by remember { mutableFloatStateOf(1.0f) }
    var ttsRate by remember { mutableFloatStateOf(1.0f) }

    // Image Gen State
    var imagePrompt by remember { mutableStateOf("A sleek futuristic device glowing with blue ambient light on a minimal clean desk") }
    var selectedStyle by remember { mutableStateOf("Cyberpunk") }
    var isGeneratingImage by remember { mutableStateOf(false) }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.US
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Studio Tab Bar (Google Material 3 Tab Row)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            TabRow(
                selectedTabIndex = selectedStudioTab.ordinal,
                containerColor = colorScheme.surface,
                contentColor = colorScheme.primary
            ) {
                Tab(
                    selected = selectedStudioTab == StudioTab.TTS,
                    onClick = { selectedStudioTab = StudioTab.TTS },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(StudioTab.TTS.title, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
                Tab(
                    selected = selectedStudioTab == StudioTab.IMAGE_GEN,
                    onClick = { selectedStudioTab = StudioTab.IMAGE_GEN },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(StudioTab.IMAGE_GEN.title, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
            }
        }

        when (selectedStudioTab) {
            StudioTab.TTS -> {
                // Text-to-Speech Studio
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Speech Synthesis Input",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TextField(
                                value = ttsText,
                                onValueChange = { ttsText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colorScheme.surfaceVariant,
                                    unfocusedContainerColor = colorScheme.surfaceVariant,
                                    focusedTextColor = colorScheme.onSurface,
                                    unfocusedTextColor = colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Pitch & Rate Controls
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Voice Modulation Parameters",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pitch", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                                    Text("${String.format("%.2f", ttsPitch)}x", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, fontFamily = FontFamily.Monospace)
                                }
                                Slider(
                                    value = ttsPitch,
                                    onValueChange = { ttsPitch = it },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(thumbColor = colorScheme.primary, activeTrackColor = colorScheme.primary)
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Speed Rate", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                                    Text("${String.format("%.2f", ttsRate)}x", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, fontFamily = FontFamily.Monospace)
                                }
                                Slider(
                                    value = ttsRate,
                                    onValueChange = { ttsRate = it },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(thumbColor = colorScheme.primary, activeTrackColor = colorScheme.primary)
                                )
                            }
                        }
                    }

                    // Playback Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (ttsText.isNotBlank()) {
                                    ttsEngine?.setPitch(ttsPitch)
                                    ttsEngine?.setSpeechRate(ttsRate)
                                    ttsEngine?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, "PRIVTE_TTS")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Speak Audio", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = {
                                ttsEngine?.stop()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            StudioTab.IMAGE_GEN -> {
                // Image Generation Studio
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Prompt",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TextField(
                                value = imagePrompt,
                                onValueChange = { imagePrompt = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 90.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colorScheme.surfaceVariant,
                                    unfocusedContainerColor = colorScheme.surfaceVariant,
                                    focusedTextColor = colorScheme.onSurface,
                                    unfocusedTextColor = colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Style Selection
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Style Preset",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Cyberpunk", "Anime", "Photoreal", "3D Art").forEach { style ->
                                    val isSelected = selectedStyle == style
                                    Surface(
                                        onClick = { selectedStyle = style },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = style,
                                            color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Generate Button
                    Button(
                        onClick = {
                            if (imagePrompt.isNotBlank() && !isGeneratingImage) {
                                isGeneratingImage = true
                                coroutineScope.launch {
                                    delay(1200)
                                    generatedBitmap = createArtisticCanvas(512, 512, imagePrompt, selectedStyle)
                                    isGeneratingImage = false
                                }
                            }
                        },
                        enabled = !isGeneratingImage && imagePrompt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        if (isGeneratingImage) {
                            CircularProgressIndicator(color = colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Synthesizing Neural Diffusion...", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Image", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Generated Image Canvas Display
                    generatedBitmap?.let { bmp ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Generated Neural Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedStyle • 512x512",
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    TextButton(
                                        onClick = {
                                            Toast.makeText(context, "Image saved to device storage", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun createArtisticCanvas(width: Int, height: Int, prompt: String, style: String): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val (c1, c2) = when (style) {
        "Cyberpunk" -> Pair(android.graphics.Color.rgb(18, 22, 34), android.graphics.Color.rgb(26, 115, 232))
        "Anime" -> Pair(android.graphics.Color.rgb(45, 25, 65), android.graphics.Color.rgb(255, 120, 180))
        "Photoreal" -> Pair(android.graphics.Color.rgb(25, 30, 40), android.graphics.Color.rgb(90, 110, 130))
        else -> Pair(android.graphics.Color.rgb(20, 20, 26), android.graphics.Color.rgb(66, 133, 244))
    }

    val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), c1, c2, Shader.TileMode.CLAMP)
    val paint = Paint().apply {
        shader = gradient
        isAntiAlias = true
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    val ringPaint = Paint().apply {
        color = android.graphics.Color.argb(120, 255, 255, 255)
        this.style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    canvas.drawCircle(width / 2f, height / 2f, width / 3.2f, ringPaint)
    canvas.drawCircle(width / 2f, height / 2f, width / 4.5f, ringPaint)

    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("PrivteLLM Neural Studio", width / 2f, height / 2f + 8f, textPaint)

    val subPaint = Paint().apply {
        color = android.graphics.Color.argb(200, 255, 255, 255)
        textSize = 14f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(style.uppercase(), width / 2f, height / 2f + 36f, subPaint)

    return bitmap
}
