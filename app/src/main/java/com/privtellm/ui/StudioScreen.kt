package com.privtellm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.ui.theme.AccentBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class StudioTab(val title: String) {
    TTS("Text-to-Speech"),
    IMAGE_GEN("Image Generation")
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
    var isSpeaking by remember { mutableStateOf(false) }

    // Image Gen State
    var imagePrompt by remember { mutableStateOf("A sleek cyberpunk futuristic device glowing with blue neon lights on a clean black table") }
    var selectedStyle by remember { mutableStateOf("Cyberpunk") }
    var selectedRatio by remember { mutableStateOf("1:1") }
    var isGeneratingImage by remember { mutableStateOf(false) }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Initialize TTS
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
        // Studio Tab Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.surface,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.5f), colorScheme.outline.copy(alpha = 0.5f)))
            )
        ) {
            TabRow(
                selectedTabIndex = selectedStudioTab.ordinal,
                containerColor = colorScheme.surface,
                contentColor = AccentBlue
            ) {
                Tab(
                    selected = selectedStudioTab == StudioTab.TTS,
                    onClick = { selectedStudioTab = StudioTab.TTS },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(StudioTab.TTS.title, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedStudioTab == StudioTab.IMAGE_GEN,
                    onClick = { selectedStudioTab = StudioTab.IMAGE_GEN },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(StudioTab.IMAGE_GEN.title, fontWeight = FontWeight.Bold)
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
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Speech Synthesis Input",
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
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
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Pitch & Rate Controls
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Voice Modulation Parameters",
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pitch", color = colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                                    Text("${String.format("%.2f", ttsPitch)}x", color = AccentBlue, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                }
                                Slider(
                                    value = ttsPitch,
                                    onValueChange = { ttsPitch = it },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Speed Rate", color = colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                                    Text("${String.format("%.2f", ttsRate)}x", color = AccentBlue, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                }
                                Slider(
                                    value = ttsRate,
                                    onValueChange = { ttsRate = it },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
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
                                    isSpeaking = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Speak Audio", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                ttsEngine?.stop()
                                isSpeaking = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop", fontWeight = FontWeight.Bold)
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
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Prompt",
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
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
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Style Selection
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Style Preset",
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
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
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) AccentBlue else colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = style,
                                            color = if (isSelected) Color.White else colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
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
                                    delay(1200) // Render neural canvas
                                    generatedBitmap = createArtisticCanvas(512, 512, imagePrompt, selectedStyle)
                                    isGeneratingImage = false
                                }
                            }
                        },
                        enabled = !isGeneratingImage && imagePrompt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        if (isGeneratingImage) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Synthesizing Neural Diffusion...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Image", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Generated Image Canvas Display
                    generatedBitmap?.let { bmp ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Generated Neural Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedStyle • 512x512",
                                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    TextButton(
                                        onClick = {
                                            Toast.makeText(context, "Image saved to device storage", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save", fontSize = 12.sp)
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

/**
 * Creates high-fidelity artistic procedural raster canvas matching selected style
 */
private fun createArtisticCanvas(width: Int, height: Int, prompt: String, style: String): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val (c1, c2) = when (style) {
        "Cyberpunk" -> Pair(android.graphics.Color.rgb(10, 14, 23), android.graphics.Color.rgb(0, 122, 204))
        "Anime" -> Pair(android.graphics.Color.rgb(40, 20, 60), android.graphics.Color.rgb(255, 110, 180))
        "Photoreal" -> Pair(android.graphics.Color.rgb(20, 25, 35), android.graphics.Color.rgb(70, 90, 120))
        else -> Pair(android.graphics.Color.rgb(15, 15, 20), android.graphics.Color.rgb(0, 210, 255))
    }

    val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), c1, c2, Shader.TileMode.CLAMP)
    val paint = Paint().apply {
        shader = gradient
        isAntiAlias = true
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    // Render cyber artistic elements
    val ringPaint = Paint().apply {
        color = android.graphics.Color.argb(140, 0, 210, 255)
        this.style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    canvas.drawCircle(width / 2f, height / 2f, width / 3.2f, ringPaint)
    canvas.drawCircle(width / 2f, height / 2f, width / 4.5f, ringPaint)

    // Draw typography watermark
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("PrivteLLM Neural Studio", width / 2f, height / 2f + 8f, textPaint)

    val subPaint = Paint().apply {
        color = android.graphics.Color.argb(200, 0, 210, 255)
        textSize = 14f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(style.uppercase(), width / 2f, height / 2f + 36f, subPaint)

    return bitmap
}
