package com.privtellm.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.core.profiler.DeviceProfiler
import com.privtellm.hub.DownloadStatus
import com.privtellm.hub.ModelCapability
import com.privtellm.hub.ModelCatalog
import com.privtellm.hub.ModelDownloader
import com.privtellm.hub.ModelItem
import kotlinx.coroutines.launch

enum class ModelCategory(val label: String) {
    ALL("All"),
    RECOMMENDED("Recommended"),
    COMPACT("≤1B Compact"),
    BALANCED("1B–3B"),
    VISION("Vision"),
    STUDIO("Audio & Gen"),
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
            icon = {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Importing Custom GGUF Model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Copying model weights into app storage...", style = MaterialTheme.typography.bodyMedium)
                    if (importProgress > 0f) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { importProgress },
                            color = colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${(importProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {},
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
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
        // Hub Header & Import Button
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model Store",
                            style = MaterialTheme.typography.headlineMedium,
                            color = colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Curated local GGUF weights (0.5B – 14B)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    // Import Custom .GGUF Button
                    FilledTonalButton(
                        onClick = { customGgufPicker.launch("*/*") },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar (Google Play Store Style)
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search model by name or quant...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Category Chips (Google Material 3 Pill Chips)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ModelCategory.values()) { category ->
                        val isSelected = (selectedCategory == category)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) colorScheme.secondaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = if (!isSelected) BorderStroke(1.dp, colorScheme.outlineVariant) else null,
                            modifier = Modifier.clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = category.label,
                                color = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.onPrimaryContainer)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Active in RAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = activeModel.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onUnloadModel,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Unload", style = MaterialTheme.typography.labelSmall)
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
    val estimatedRamMb = (model.sizeBytes / (1024 * 1024)) + 400L
    val (ramCheckColor, ramCheckBg, ramCheckLabel) = when {
        estimatedRamMb <= freeRamMb - 300 -> Triple(colorScheme.tertiary, colorScheme.tertiaryContainer, "Safe Fit")
        estimatedRamMb <= freeRamMb -> Triple(colorScheme.primary, colorScheme.primaryContainer, "Tight Fit")
        else -> Triple(colorScheme.error, colorScheme.errorContainer, "OOM Risk")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, if (isLoaded) colorScheme.primary else colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Model Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLoaded) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (model.capability) {
                                    ModelCapability.VISION_MULTIMODAL -> Icons.Default.Visibility
                                    ModelCapability.TEXT_TO_SPEECH -> Icons.Default.VolumeUp
                                    ModelCapability.IMAGE_GENERATION -> Icons.Default.Palette
                                    else -> Icons.Default.SmartToy
                                },
                                contentDescription = null,
                                tint = if (isLoaded) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${model.parametersBillion}B params • ${model.quantization} • ${model.sizeFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isLoaded) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // DYNAMIC RECOMMENDATION BADGE (Clean Google Green Tonal Banner)
            if (isRecommended) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recommended for your device",
                            color = colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description / Recommendation Details
            Text(
                text = if (isRecommended) recommendationDetails else model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Specs & RAM Safety Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${model.contextLength} ctx",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = model.quantization,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ramCheckBg
                ) {
                    Text(
                        text = "● $ramCheckLabel (~${estimatedRamMb}MB)",
                        color = ramCheckColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Error Banner
            if (model.status == DownloadStatus.ERROR && model.lastError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = model.lastError ?: "Download failed. Tap to retry.",
                            color = colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
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
                                "Downloading ${(model.downloadProgress * 100).toInt()}% ($downloadedMb / ${model.sizeFormatted})"
                            else "Paused ($downloadedMb / ${model.sizeFormatted})",
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (model.downloadSpeedMb > 0)
                                "${String.format("%.1f", model.downloadSpeedMb)} MB/s"
                            else "-- MB/s",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
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
                        color = colorScheme.primary,
                        trackColor = colorScheme.surfaceVariant
                    )
                }
            }

            if (model.status == DownloadStatus.VERIFYING) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying SHA-256 hash...", color = colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row (Google Material 3 Filled & Tonal Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (model.status) {
                    DownloadStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    DownloadStatus.ERROR -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause", style = MaterialTheme.typography.labelLarge)
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
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume", style = MaterialTheme.typography.labelLarge)
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
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isLoaded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onChatNow,
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Chat Now", style = MaterialTheme.typography.labelLarge)
                                }

                                OutlinedButton(
                                    onClick = onUnload,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
                                    border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Unload", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        } else {
                            Button(
                                onClick = onLoad,
                                enabled = !model.isLoading,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                if (model.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Loading...", style = MaterialTheme.typography.labelLarge)
                                } else {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load into RAM", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
