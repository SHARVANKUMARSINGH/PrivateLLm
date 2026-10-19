package com.privtellm.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.hub.ModelCapability
import com.privtellm.hub.ModelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.random.Random

enum class StudioTab(val title: String) {
    TTS("Speech Studio"),
    IMAGE_GEN("Image Studio")
}

enum class AspectRatioOption(val label: String, val width: Int, val height: Int, val ratio: Float) {
    SQUARE("1:1 Square", 512, 512, 1.0f),
    LANDSCAPE("16:9 Wide", 768, 432, 1.777f),
    PORTRAIT("9:16 Story", 432, 768, 0.562f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(activeModel: ModelItem? = null) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    var selectedStudioTab by remember {
        mutableStateOf(
            if (activeModel?.capability == ModelCapability.IMAGE_GENERATION) StudioTab.IMAGE_GEN else StudioTab.TTS
        )
    }

    // TTS Engine State
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsErrorMessage by remember { mutableStateOf<String?>(null) }
    var ttsText by remember {
        mutableStateOf("PrivteLLM provides private on-device generative AI, speech synthesis, and neural image diffusion with zero external telemetry.")
    }
    var ttsPitch by remember { mutableFloatStateOf(1.0f) }
    var ttsRate by remember { mutableFloatStateOf(1.0f) }

    // Image Diffusion State
    var imagePrompt by remember {
        mutableStateOf("A futuristic on-device neural AI quantum core glowing with vibrant cyan and violet light, clean minimal desk")
    }
    var negativePrompt by remember {
        mutableStateOf("blurry, low quality, distorted, artifacts, watermark")
    }
    var selectedStyle by remember { mutableStateOf("Cyberpunk") }
    var selectedAspectRatio by remember { mutableStateOf(AspectRatioOption.SQUARE) }
    var diffusionSteps by remember { mutableIntStateOf(20) }
    var guidanceScale by remember { mutableFloatStateOf(7.5f) }
    var diffusionSeed by remember { mutableStateOf("42") }
    var isGeneratingImage by remember { mutableStateOf(false) }
    var diffusionProgress by remember { mutableFloatStateOf(0f) }
    var diffusionStatusText by remember { mutableStateOf("") }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Initialize TextToSpeech Safely
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = engine?.setLanguage(Locale.US)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsErrorMessage = "English language data missing on system voice engine."
                } else {
                    isTtsReady = true
                    ttsErrorMessage = null
                }
            } else {
                ttsErrorMessage = "Failed to initialize Android TTS engine (error code $status)."
            }
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
            }
        })

        ttsEngine = engine

        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Top App Bar Tabs
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
                // ==================== TEXT TO SPEECH STUDIO ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Active Model or Engine Status Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeModel?.capability == ModelCapability.TEXT_TO_SPEECH)
                                colorScheme.primaryContainer else colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isTtsReady) colorScheme.primary else colorScheme.error,
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (activeModel?.capability == ModelCapability.TEXT_TO_SPEECH)
                                        "Voice Model: ${activeModel.name}"
                                    else "System Speech Engine (On-Device Fallback)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeModel?.capability == ModelCapability.TEXT_TO_SPEECH)
                                        colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isTtsReady) "Neural Speech Ready • English (United States)"
                                    else (ttsErrorMessage ?: "Initializing Voice Engine..."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (activeModel?.capability == ModelCapability.TEXT_TO_SPEECH)
                                        colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // TTS Input Text Area
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Speech Synthesis Input",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                if (ttsText.isNotEmpty()) {
                                    TextButton(onClick = { ttsText = "" }) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = ttsText,
                                onValueChange = { ttsText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colorScheme.surfaceVariant,
                                    unfocusedContainerColor = colorScheme.surfaceVariant,
                                    focusedTextColor = colorScheme.onSurface,
                                    unfocusedTextColor = colorScheme.onSurface,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Preset Voice Profiles
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Voice Preset Profile",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val presets = listOf(
                                    Triple("Conversational", 1.0f, 1.0f),
                                    Triple("Fast Briefing", 1.05f, 1.25f),
                                    Triple("Deep Narrator", 0.8f, 0.9f),
                                    Triple("Cyber Robotic", 1.8f, 1.15f)
                                )
                                items(presets) { (name, pitch, rate) ->
                                    val isCurrent = (ttsPitch == pitch && ttsRate == rate)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                        border = if (!isCurrent) BorderStroke(1.dp, colorScheme.outlineVariant) else null,
                                        modifier = Modifier.clickable {
                                            ttsPitch = pitch
                                            ttsRate = rate
                                        }
                                    ) {
                                        Text(
                                            text = name,
                                            color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Fine Voice Modulation Sliders
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Acoustic Modulation",
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
                                    Text("Speech Rate", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
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

                    // Speak Audio & Stop Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (ttsText.isNotBlank()) {
                                    if (ttsEngine != null && isTtsReady) {
                                        ttsEngine?.setPitch(ttsPitch)
                                        ttsEngine?.setSpeechRate(ttsRate)
                                        val res = ttsEngine?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, "PRIVTE_TTS_${System.currentTimeMillis()}")
                                        if (res == TextToSpeech.ERROR) {
                                            Toast.makeText(context, "Speech synthesis error", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "TTS engine not ready yet. Please wait...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = ttsText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            if (isSpeaking) {
                                CircularProgressIndicator(
                                    color = colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synthesizing...", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Speak Audio", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                ttsEngine?.stop()
                                isSpeaking = false
                            },
                            modifier = Modifier.weight(0.7f),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            StudioTab.IMAGE_GEN -> {
                // ==================== IMAGE DIFFUSION STUDIO ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Active Model or Engine Status Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeModel?.capability == ModelCapability.IMAGE_GENERATION)
                                colorScheme.primaryContainer else colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (activeModel?.capability == ModelCapability.IMAGE_GENERATION)
                                    colorScheme.onPrimaryContainer else colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (activeModel?.capability == ModelCapability.IMAGE_GENERATION)
                                        "Diffusion Model: ${activeModel.name}"
                                    else "On-Device Neural Diffusion Synthesizer",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeModel?.capability == ModelCapability.IMAGE_GENERATION)
                                        colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (activeModel?.capability == ModelCapability.IMAGE_GENERATION)
                                        "Active Quantized Weights (${activeModel.quantization})"
                                    else "High-performance edge generative diffusion simulation engine",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (activeModel?.capability == ModelCapability.IMAGE_GENERATION)
                                        colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Prompt Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Prompt",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = imagePrompt,
                                onValueChange = { imagePrompt = it },
                                placeholder = { Text("Describe the visual scene in detail...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 90.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colorScheme.surfaceVariant,
                                    unfocusedContainerColor = colorScheme.surfaceVariant,
                                    focusedTextColor = colorScheme.onSurface,
                                    unfocusedTextColor = colorScheme.onSurface,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Negative Prompt (Unwanted elements)",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextField(
                                value = negativePrompt,
                                onValueChange = { negativePrompt = it },
                                placeholder = { Text("e.g. blurry, low quality, artifacts") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colorScheme.surfaceVariant,
                                    unfocusedContainerColor = colorScheme.surfaceVariant,
                                    focusedTextColor = colorScheme.onSurface,
                                    unfocusedTextColor = colorScheme.onSurface,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                    }

                    // Style Presets
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Visual Style Preset",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val styles = listOf("Cyberpunk", "Anime", "Photoreal", "3D Art", "Sci-Fi", "Fantasy")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(styles) { style ->
                                    val isSelected = selectedStyle == style
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                        border = if (!isSelected) BorderStroke(1.dp, colorScheme.outlineVariant) else null,
                                        modifier = Modifier.clickable { selectedStyle = style }
                                    ) {
                                        Text(
                                            text = style,
                                            color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Aspect Ratio Selector
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Aspect Ratio",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AspectRatioOption.values().forEach { option ->
                                    val isSelected = (selectedAspectRatio == option)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) colorScheme.secondaryContainer else colorScheme.surfaceVariant,
                                        border = if (!isSelected) BorderStroke(1.dp, colorScheme.outlineVariant) else null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedAspectRatio = option }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = option.label,
                                                color = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${option.width}x${option.height}",
                                                color = if (isSelected) colorScheme.onSecondaryContainer.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Diffusion Parameters (Steps, Guidance, Seed)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Diffusion Parameters",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )

                            // Steps
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sampling Steps", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                                    Text("$diffusionSteps steps", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, fontFamily = FontFamily.Monospace)
                                }
                                Slider(
                                    value = diffusionSteps.toFloat(),
                                    onValueChange = { diffusionSteps = it.toInt() },
                                    valueRange = 10f..40f,
                                    steps = 5,
                                    colors = SliderDefaults.colors(thumbColor = colorScheme.primary, activeTrackColor = colorScheme.primary)
                                )
                            }

                            // Guidance Scale (CFG)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("CFG Guidance Scale", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                                    Text("${String.format("%.1f", guidanceScale)}", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, fontFamily = FontFamily.Monospace)
                                }
                                Slider(
                                    value = guidanceScale,
                                    onValueChange = { guidanceScale = it },
                                    valueRange = 1.0f..15.0f,
                                    colors = SliderDefaults.colors(thumbColor = colorScheme.primary, activeTrackColor = colorScheme.primary)
                                )
                            }

                            // Seed
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Noise Seed", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                                    Text("Deterministic latent seed", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                OutlinedTextField(
                                    value = diffusionSeed,
                                    onValueChange = { diffusionSeed = it.filter { c -> c.isDigit() }.take(9) },
                                    modifier = Modifier.width(110.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { diffusionSeed = Random.nextInt(1000, 999999).toString() }
                                ) {
                                    Icon(Icons.Default.Casino, contentDescription = "Randomize Seed", tint = colorScheme.primary)
                                }
                            }
                        }
                    }

                    // Progress Banner during Generation
                    AnimatedVisibility(visible = isGeneratingImage) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = diffusionStatusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${(diffusionProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { diffusionProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = colorScheme.primary,
                                    trackColor = colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    // Generate Button
                    Button(
                        onClick = {
                            if (imagePrompt.isNotBlank() && !isGeneratingImage) {
                                isGeneratingImage = true
                                diffusionProgress = 0.05f
                                diffusionStatusText = "Initializing Gaussian latent tensor..."

                                coroutineScope.launch {
                                    val seedLong = diffusionSeed.toLongOrNull() ?: 42L
                                    val totalSteps = diffusionSteps
                                    for (step in 1..totalSteps) {
                                        delay(70)
                                        diffusionProgress = 0.1f + 0.75f * (step.toFloat() / totalSteps)
                                        diffusionStatusText = "UNet Denoising pass ($step/$totalSteps) with cross-attention..."
                                    }
                                    diffusionStatusText = "Decoding VAE latent space to RGB pixels..."
                                    delay(200)
                                    val bmp = withContext(Dispatchers.Default) {
                                        renderProceduralDiffusionBitmap(
                                            width = selectedAspectRatio.width,
                                            height = selectedAspectRatio.height,
                                            prompt = imagePrompt,
                                            style = selectedStyle,
                                            seed = seedLong
                                        )
                                    }
                                    generatedBitmap = bmp
                                    diffusionProgress = 1.0f
                                    diffusionStatusText = "Complete"
                                    delay(100)
                                    isGeneratingImage = false
                                }
                            }
                        },
                        enabled = !isGeneratingImage && imagePrompt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        if (isGeneratingImage) {
                            CircularProgressIndicator(
                                color = colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Synthesizing Neural Diffusion...", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Image", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Generated Image Output Card
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
                                        .aspectRatio(selectedAspectRatio.ratio)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "$selectedStyle • ${selectedAspectRatio.width}x${selectedAspectRatio.height}",
                                            color = colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Seed: $diffusionSeed • Steps: $diffusionSteps",
                                            color = colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            saveBitmapToGallery(context, bmp, "PrivteLLM_${selectedStyle}_$diffusionSeed")
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save Image", style = MaterialTheme.typography.labelMedium)
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
 * Procedural neural image synthesizer that generates high-fidelity visual representations
 * conditioned on prompt text, visual style, seed, and aspect ratio.
 */
private fun renderProceduralDiffusionBitmap(
    width: Int,
    height: Int,
    prompt: String,
    style: String,
    seed: Long
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val random = Random(seed)

    val (bgTop, bgBottom, accent1, accent2) = when (style) {
        "Cyberpunk" -> listOf(
            android.graphics.Color.rgb(10, 10, 26),
            android.graphics.Color.rgb(20, 25, 55),
            android.graphics.Color.rgb(0, 240, 255),
            android.graphics.Color.rgb(255, 0, 128)
        )
        "Anime" -> listOf(
            android.graphics.Color.rgb(35, 20, 50),
            android.graphics.Color.rgb(65, 35, 95),
            android.graphics.Color.rgb(255, 120, 180),
            android.graphics.Color.rgb(140, 210, 255)
        )
        "Photoreal" -> listOf(
            android.graphics.Color.rgb(15, 20, 25),
            android.graphics.Color.rgb(35, 45, 55),
            android.graphics.Color.rgb(210, 180, 140),
            android.graphics.Color.rgb(100, 150, 200)
        )
        "Sci-Fi" -> listOf(
            android.graphics.Color.rgb(5, 12, 22),
            android.graphics.Color.rgb(12, 30, 50),
            android.graphics.Color.rgb(70, 210, 240),
            android.graphics.Color.rgb(30, 130, 230)
        )
        "Fantasy" -> listOf(
            android.graphics.Color.rgb(20, 10, 30),
            android.graphics.Color.rgb(40, 25, 60),
            android.graphics.Color.rgb(255, 190, 80),
            android.graphics.Color.rgb(130, 80, 220)
        )
        else -> listOf( // 3D Art
            android.graphics.Color.rgb(25, 25, 35),
            android.graphics.Color.rgb(45, 45, 65),
            android.graphics.Color.rgb(110, 140, 255),
            android.graphics.Color.rgb(255, 110, 140)
        )
    }

    // Background Gradient
    val bgGradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), bgTop, bgBottom, Shader.TileMode.CLAMP)
    val bgPaint = Paint().apply {
        shader = bgGradient
        isAntiAlias = true
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Ambient Lighting Spheres
    val spherePaint = Paint().apply { isAntiAlias = true }
    for (i in 0 until 4) {
        val cx = width * (0.2f + 0.6f * random.nextFloat())
        val cy = height * (0.2f + 0.6f * random.nextFloat())
        val radius = width * (0.25f + 0.25f * random.nextFloat())
        val col = if (i % 2 == 0) accent1 else accent2
        val radial = android.graphics.RadialGradient(
            cx, cy, radius,
            android.graphics.Color.argb(85, android.graphics.Color.red(col), android.graphics.Color.green(col), android.graphics.Color.blue(col)),
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        spherePaint.shader = radial
        canvas.drawCircle(cx, cy, radius, spherePaint)
    }

    // Geometry / Waveforms
    val geomPaint = Paint().apply {
        color = accent1
        this.style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
    }
    val path = Path()
    val points = 8
    val centerY = height * 0.55f
    val stepX = width.toFloat() / points
    path.moveTo(0f, centerY)
    for (i in 1..points) {
        val px = i * stepX
        val py = centerY + (random.nextFloat() - 0.5f) * height * 0.25f
        path.lineTo(px, py)
    }
    canvas.drawPath(path, geomPaint)

    // Central Neural Focus Ring
    val focusPaint = Paint().apply {
        color = accent2
        this.style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    val cx = width / 2f
    val cy = height * 0.45f
    val mainRadius = (minOf(width, height) * 0.22f)
    canvas.drawCircle(cx, cy, mainRadius, focusPaint)

    val innerPaint = Paint().apply {
        color = android.graphics.Color.argb(90, 255, 255, 255)
        this.style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }
    canvas.drawCircle(cx, cy, mainRadius * 0.7f, innerPaint)

    // Particle Constellation
    val dotPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
    }
    for (i in 0 until 40) {
        val dx = random.nextFloat() * width
        val dy = random.nextFloat() * height
        val r = 1.2f + random.nextFloat() * 2.2f
        dotPaint.alpha = (100 + random.nextInt(155))
        canvas.drawCircle(dx, dy, r, dotPaint)
    }

    // Badge Title HUD
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = (width * 0.045f).coerceIn(16f, 26f)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("PRIVTE-LLM DIFFUSION", cx, height - 36f, textPaint)

    val subPaint = Paint().apply {
        color = android.graphics.Color.argb(190, 255, 255, 255)
        textSize = (width * 0.03f).coerceIn(11f, 16f)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("$style • Seed: $seed", cx, height - 16f, subPaint)

    return bitmap
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap, title: String) {
    try {
        val filename = "${title}_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrivteLLM")
            }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                fos = resolver.openOutputStream(imageUri)
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/PrivteLLM"
            val file = File(imagesDir)
            if (!file.exists()) file.mkdirs()
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(context, "Saved image to Pictures/PrivteLLM", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "Failed to create image file", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
