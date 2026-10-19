package com.privtellm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.core.profiler.DeviceProfiler
import com.privtellm.hub.DownloadStatus
import com.privtellm.hub.ModelDownloader
import com.privtellm.hub.ModelItem
import com.privtellm.ui.theme.*

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.privtellm.hub.ModelCapability
import com.privtellm.hub.ModelCatalog
import kotlinx.coroutines.launch

enum class ModelCategory(val label: String) {
    ALL("All Models"),
    RECOMMENDED("★ Recommended"),
    COMPACT("≤1B Compact"),
    BALANCED("1B–3B Balanced"),
    VISION("👁️ Vision"),
    STUDIO("🎨 Audio & Gen"),
    FLAGSHIP("7B+ Heavy")
}

@Composable
fun ModelCatalogScreen(
    models: List<ModelItem>,
    profiler: DeviceProfiler,
    downloader: ModelDownloader,
    loadedModelId: String?,
    onLoadModel: (ModelItem) -> Unit,
    onUnloadModel: () -> Unit,
    onNavigateToChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ModelCategory.ALL) }
    val memorySnapshot = profiler.getMemorySnapshot()

    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }

    val customGgufPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            importProgress = 0f
            coroutineScope.launch {
                val result = ModelCatalog.importCustomGguf(context, uri) { progress ->
                    importProgress = progress
                }
                isImporting = false
                result.onSuccess { imported ->
                    Toast.makeText(context, "Imported custom model: ${imported.name}", Toast.LENGTH_SHORT).show()
                    onLoadModel(imported)
                }.onFailure { err ->
                    Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Filter models
    val filteredModels = remember(models, searchQuery, selectedCategory, memorySnapshot) {
        models.filter { model ->
            val matchesSearch = searchQuery.isBlank() ||
                    model.name.contains(searchQuery, ignoreCase = true) ||
                    model.quantization.contains(searchQuery, ignoreCase = true)

            val isRecommended = profiler.isModelRecommended(model.parametersBillion)

            val matchesCategory = when (selectedCategory) {
                ModelCategory.ALL -> true
                ModelCategory.RECOMMENDED -> isRecommended
                ModelCategory.COMPACT -> model.parametersBillion <= 1.0f && model.capability == ModelCapability.TEXT_LLM
                ModelCategory.BALANCED -> model.parametersBillion in 1.1f..4.0f && model.capability == ModelCapability.TEXT_LLM
                ModelCategory.VISION -> model.capability == ModelCapability.VISION_MULTIMODAL
                ModelCategory.STUDIO -> model.capability == ModelCapability.TEXT_TO_SPEECH || model.capability == ModelCapability.IMAGE_GENERATION
                ModelCategory.FLAGSHIP -> model.parametersBillion >= 6.0f
            }

            matchesSearch && matchesCategory
        }
    }

    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing Custom GGUF Model", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Copying model weights into app storage...", fontSize = 13.sp)
                    if (importProgress > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { importProgress },
                            color = AccentBlue,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${(importProgress * 100).toInt()}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {}
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
    ) {
        // Hub Banner & Search
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "QUANTIZED MODEL STORE",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Local GGUF Models (0.5B – 20B)",
                            color = colorScheme.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Import Custom .GGUF Button
                    Button(
                        onClick = { customGgufPicker.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .GGUF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }


                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search model by name or quant (Q4_K_M)...",
                            color = colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surface,
                        unfocusedContainerColor = colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ModelCategory.values()) { category ->
                        val isSelected = (selectedCategory == category)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) AccentBlue else colorScheme.surface,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    if (isSelected) listOf(AccentBlue, AccentBlue)
                                    else listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f))
                                )
                            ),
                            modifier = Modifier.clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = category.label,
                                color = if (isSelected) Color.White else colorScheme.onSurface.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Active Model Notification if loaded
        if (loadedModelId != null) {
            val activeModel = models.firstOrNull { it.id == loadedModelId }
            if (activeModel != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = AccentBlueGlow,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(AccentBlue, AccentBlue.copy(alpha = 0.4f)))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("ACTIVE ENGINE IN RAM", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(activeModel.name, color = colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onUnloadModel,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarning),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(listOf(StatusWarning, StatusWarning.copy(alpha = 0.5f)))
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Unload", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Models List
        items(filteredModels, key = { it.id }) { model ->
            val isRecommended = profiler.isModelRecommended(model.parametersBillion)
            val recommendationDetails = profiler.getRecommendationDetails(model.parametersBillion)
            val modelStateFlow = downloader.getStateFlow(model)
            val currentModel by modelStateFlow.collectAsState()
            val isLoaded = (loadedModelId == currentModel.id)

            ModelCardItem(
                model = currentModel,
                isRecommended = isRecommended,
                recommendationDetails = recommendationDetails,
                isLoaded = isLoaded,
                freeRamMb = memorySnapshot.availableRamMb,
                onDownload = { downloader.startDownload(currentModel) },
                onPause = { downloader.pauseDownload(currentModel) },
                onLoad = { onLoadModel(currentModel) },
                onUnload = onUnloadModel,
                onDelete = { downloader.deleteModel(currentModel) },
                onChatNow = onNavigateToChat
            )
        }
    }
}

@Composable
fun ModelCardItem(
    model: ModelItem,
    isRecommended: Boolean,
    recommendationDetails: String,
    isLoaded: Boolean,
    freeRamMb: Long,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onChatNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = when {
        isLoaded -> AccentBlue
        isRecommended -> AccentBlue
        else -> colorScheme.outline.copy(alpha = 0.45f)
    }

    val estimatedRamMb = (model.sizeBytes / (1024 * 1024)) + 400L
    val (ramCheckColor, ramCheckLabel) = when {
        estimatedRamMb <= freeRamMb - 300 -> Pair(AccentBlue, "SAFE FIT")
        estimatedRamMb <= freeRamMb -> Pair(StatusWarning, "TIGHT FIT")
        else -> Pair(StatusCritical, "HIGH OOM RISK")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(borderColor.copy(alpha = if (isLoaded) 0.9f else 0.5f), colorScheme.outline.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Model Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${model.parametersBillion}B params • ${model.quantization} • ${model.sizeFormatted}",
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isLoaded) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentBlueGlow,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(AccentBlue, AccentBlue))
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AccentBlue)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE",
                                color = AccentBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // DYNAMIC RECOMMENDATION BADGE (Requirement 2)
            if (isRecommended) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentBlueGlow,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(AccentBlue, AccentBlue.copy(alpha = 0.4f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★ [RECOMMENDED FOR YOUR DEVICE]",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description / Recommendation Details
            Text(
                text = if (isRecommended) recommendationDetails else model.description,
                color = if (isRecommended) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Specs & RAM Safety Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${model.contextLength} ctx",
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = model.quantization,
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ramCheckColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "● $ramCheckLabel (~${estimatedRamMb}MB)",
                        color = ramCheckColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Error Banner if download or loading failed
            if (model.status == DownloadStatus.ERROR && model.lastError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusCriticalGlow,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(StatusCritical, StatusCritical.copy(alpha = 0.5f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StatusCritical,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = model.lastError ?: "Download failed. Tap to retry.",
                            color = colorScheme.onSurface,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            // Download Progress Bar & Speed
            if (model.status == DownloadStatus.DOWNLOADING || model.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val downloadedMb = String.format("%.1f MB", model.downloadedBytes / (1024.0 * 1024.0))
                        Text(
                            text = if (model.status == DownloadStatus.DOWNLOADING)
                                "Downloading ${String.format("%.1f", model.downloadProgress * 100)}% ($downloadedMb / ${model.sizeFormatted})"
                            else "Paused ($downloadedMb / ${model.sizeFormatted})",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (model.downloadSpeedMb > 0)
                                "${String.format("%.1f", model.downloadSpeedMb)} MB/s"
                            else "-- MB/s",
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { model.downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentBlue,
                        trackColor = colorScheme.surfaceVariant
                    )
                }
            }

            if (model.status == DownloadStatus.VERIFYING) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying SHA-256 integrity hash...", color = AccentBlue, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row with Modern Material 3 Styling
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (model.status) {
                    DownloadStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download GGUF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DownloadStatus.ERROR -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Download", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    DownloadStatus.PAUSED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Part",
                                    tint = colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Button(
                                onClick = onDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    DownloadStatus.VERIFYING -> {}

                    DownloadStatus.DOWNLOADED, DownloadStatus.LOADED -> {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Model",
                                tint = colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isLoaded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onChatNow,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Chat Now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onUnload,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarning),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.horizontalGradient(listOf(StatusWarning, StatusWarning.copy(alpha = 0.5f)))
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text("Unload", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = onLoad,
                                enabled = !model.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                if (model.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Loading RAM...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load Engine", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
