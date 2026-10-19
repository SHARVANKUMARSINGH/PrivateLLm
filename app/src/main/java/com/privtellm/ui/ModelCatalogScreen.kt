package com.privtellm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

enum class ModelCategory(val label: String) {
    ALL("All Models"),
    RECOMMENDED("★ Recommended"),
    COMPACT("≤1B Compact"),
    BALANCED("1B–3B Balanced"),
    HEAVY("7B–8B Heavy"),
    FLAGSHIP("14B+ Flagship")
}

@Composable
fun ModelCatalogScreen(
    models: List<ModelItem>,
    profiler: DeviceProfiler,
    downloader: ModelDownloader,
    loadedModelId: String?,
    onLoadModel: (ModelItem) -> Unit,
    onUnloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ModelCategory.ALL) }
    val memorySnapshot = profiler.getMemorySnapshot()

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
                ModelCategory.COMPACT -> model.parametersBillion <= 1.0f
                ModelCategory.BALANCED -> model.parametersBillion in 1.1f..4.0f
                ModelCategory.HEAVY -> model.parametersBillion in 6.0f..9.0f
                ModelCategory.FLAGSHIP -> model.parametersBillion >= 12.0f
            }

            matchesSearch && matchesCategory
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
    ) {
        // Hub Banner
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "QUANTIZED MODEL HUB",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "GGUF Model Store (0.5B – 20B)",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpaceSurfaceVariant,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(SpaceCardBorder, SpaceCardBorder))
                        )
                    ) {
                        Text(
                            text = "${models.size} Models",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, quant (Q4_K_M), or size...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpaceSurface,
                        unfocusedContainerColor = SpaceSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
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
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NeonGreenGlow else SpaceSurface,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    if (isSelected) listOf(NeonGreen, NeonGreen) else listOf(SpaceCardBorder, SpaceCardBorder)
                                )
                            ),
                            modifier = Modifier.clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = category.label,
                                color = if (isSelected) NeonGreen else TextSecondary,
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
                        shape = RoundedCornerShape(14.dp),
                        color = NeonGreenGlow,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(NeonGreen, NeonGreen.copy(alpha = 0.3f)))
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
                                        .background(NeonGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("ACTIVE ENGINE IN RAM", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(activeModel.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onUnloadModel,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(listOf(AmberAccent, AmberAccent.copy(alpha = 0.5f)))
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Unload", fontSize = 11.sp)
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

            UltraModelCard(
                model = currentModel,
                isRecommended = isRecommended,
                recommendationDetails = recommendationDetails,
                isLoaded = isLoaded,
                freeRamMb = memorySnapshot.availableRamMb,
                onDownload = { downloader.startDownload(currentModel) },
                onPause = { downloader.pauseDownload(currentModel) },
                onLoad = { onLoadModel(currentModel) },
                onUnload = onUnloadModel,
                onDelete = { downloader.deleteModel(currentModel) }
            )
        }
    }
}

@Composable
fun UltraModelCard(
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
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isLoaded -> NeonGreen
        isRecommended -> ElectricCyan
        else -> SpaceCardBorder
    }

    // Memory requirement safety check
    val estimatedRamMb = (model.sizeBytes / (1024 * 1024)) + 400L // model weights + 400MB context
    val (ramCheckColor, ramCheckLabel) = when {
        estimatedRamMb <= freeRamMb - 300 -> Pair(NeonGreen, "SAFE FIT")
        estimatedRamMb <= freeRamMb -> Pair(AmberAccent, "TIGHT FIT")
        else -> Pair(CrimsonRed, "HIGH OOM RISK")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(borderColor.copy(alpha = if (isLoaded) 0.9f else 0.5f), SpaceCardBorder)
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
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${model.parametersBillion}B params • ${model.quantization} • ${model.sizeFormatted}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isLoaded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonGreenGlow,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(NeonGreen, NeonGreen))
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
                                    .background(NeonGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE ENGINE",
                                color = NeonGreen,
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
                    shape = RoundedCornerShape(8.dp),
                    color = NeonGreen.copy(alpha = 0.12f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(NeonGreen, NeonGreen.copy(alpha = 0.3f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★ [RECOMMENDED FOR YOUR DEVICE]",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Model Description / Recommendation Rationale
            Text(
                text = if (isRecommended) recommendationDetails else model.description,
                color = if (isRecommended) TextPrimary else TextSecondary,
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
                // Technical Tag Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SpaceSurfaceVariant
                    ) {
                        Text(
                            text = "${model.contextLength} ctx",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SpaceSurfaceVariant
                    ) {
                        Text(
                            text = model.quantization,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // RAM Footprint indicator
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ramCheckColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "● $ramCheckLabel (~${estimatedRamMb}MB RAM)",
                        color = ramCheckColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
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
                        Text(
                            text = if (model.status == DownloadStatus.DOWNLOADING)
                                "Downloading (${String.format("%.1f", model.downloadProgress * 100)}%)"
                            else "Paused",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (model.downloadSpeedMb > 0)
                                "${String.format("%.1f", model.downloadSpeedMb)} MB/s"
                            else "-- MB/s",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { model.downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ElectricCyan,
                        trackColor = SpaceSurfaceVariant
                    )
                }
            }

            if (model.status == DownloadStatus.VERIFYING) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AmberAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying SHA-256 integrity hash...", color = AmberAccent, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (model.status) {
                    DownloadStatus.NOT_DOWNLOADED, DownloadStatus.ERROR -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = SpaceBackground,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download GGUF", color = SpaceBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause", fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SpaceBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", color = SpaceBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DownloadStatus.VERIFYING -> {
                        // In verification
                    }

                    DownloadStatus.DOWNLOADED, DownloadStatus.LOADED -> {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Model",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isLoaded) {
                            OutlinedButton(
                                onClick = onUnload,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(listOf(AmberAccent, AmberAccent.copy(alpha = 0.5f)))
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Unload Engine", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onLoad,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = SpaceBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load Engine", color = SpaceBackground, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
