package com.privtellm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "ON-DEVICE MODEL STORE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "Curated Quantized GGUFs (0.5B – 20B)",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Models run 100% offline via native NDK C++20 with OpenMP/NEON and Vulkan GPU acceleration.",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        items(models, key = { it.id }) { model ->
            val isRecommended = profiler.isModelRecommended(model.parametersBillion)
            val recommendationDetails = profiler.getRecommendationDetails(model.parametersBillion)
            val modelStateFlow = downloader.getStateFlow(model)
            val currentModel by modelStateFlow.collectAsState()
            val isLoaded = (loadedModelId == currentModel.id)

            ModelCard(
                model = currentModel,
                isRecommended = isRecommended,
                recommendationDetails = recommendationDetails,
                isLoaded = isLoaded,
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
fun ModelCard(
    model: ModelItem,
    isRecommended: Boolean,
    recommendationDetails: String,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isLoaded -> AccentGreen
        isRecommended -> AccentBlue
        else -> BorderColor
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(borderColor.copy(alpha = 0.8f), BorderColor)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Model Name & Parameter Tag
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
                        text = "${model.parametersBillion}B params • ${model.quantization} • ${model.sizeFormatted} • ${model.contextLength} ctx",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isLoaded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentGreen.copy(alpha = 0.2f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(AccentGreen, AccentGreen))
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
                                    .background(AccentGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE",
                                color = AccentGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // DYNAMIC RECOMMENDATION BADGE (Requirement 2)
            if (isRecommended) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentGreen.copy(alpha = 0.12f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(AccentGreen, AccentGreen.copy(alpha = 0.3f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★ [RECOMMENDED FOR YOUR DEVICE]",
                            color = AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description / Recommendation Reason
            Text(
                text = if (isRecommended) recommendationDetails else model.description,
                color = if (isRecommended) TextPrimary else TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Download Progress Bar & Speed
            if (model.status == DownloadStatus.DOWNLOADING || model.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(10.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (model.status == DownloadStatus.DOWNLOADING)
                                "Downloading (${String.format("%.1f", model.downloadProgress * 100)}%)"
                            else "Paused",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
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
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { model.downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentBlue,
                        trackColor = DarkSurfaceVariant
                    )
                }
            }

            if (model.status == DownloadStatus.VERIFYING) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Verifying SHA-256 integrity...",
                    color = AccentAmber,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download", fontSize = 12.sp)
                        }
                    }
                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause", fontSize = 12.sp)
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", fontSize = 12.sp)
                        }
                    }
                    DownloadStatus.VERIFYING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AccentAmber
                        )
                    }
                    DownloadStatus.DOWNLOADED, DownloadStatus.LOADED -> {
                        // Delete Button
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Unload", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = onLoad,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = DarkBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load Engine", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
