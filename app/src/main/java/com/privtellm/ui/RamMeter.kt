package com.privtellm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.core.profiler.MemoryPressure
import com.privtellm.core.profiler.MemorySnapshot
import com.privtellm.core.profiler.MemoryTier

@Composable
fun RamMeter(
    memorySnapshot: MemorySnapshot,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val usedRatio = memorySnapshot.usedPercentage.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = usedRatio,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "RamProgress"
    )

    val (statusColor, statusText, statusBg, statusOnBg) = when (memorySnapshot.pressure) {
        MemoryPressure.NORMAL -> Quadruple(colorScheme.primary, "Stable", colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
        MemoryPressure.MODERATE -> Quadruple(colorScheme.tertiary, "High Pressure", colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        MemoryPressure.CRITICAL -> Quadruple(colorScheme.error, "Critical (<450MB)", colorScheme.errorContainer, colorScheme.onErrorContainer)
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(durationMillis = 350),
        label = "StatusColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Profiler Icon & Pressure Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "RAM Profiler",
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hardware Profiler",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "RAM and OOM safety guardrail",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Dynamic Status Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusOnBg)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusOnBg,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Gauge & Memory Numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Semi-Arc Gauge (Google Pixel Battery Style)
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 8.dp.toPx()
                        drawArc(
                            color = colorScheme.surfaceVariant,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = animatedStatusColor,
                            startAngle = 135f,
                            sweepAngle = 270f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(usedRatio * 100).toInt()}%",
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "USED",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                // Telemetry Metrics Grid (Clean Tonal Cells)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MemoryStatCell(
                            label = "Total RAM",
                            value = "${String.format("%.1f", memorySnapshot.totalRamGb)} GB",
                            valueColor = colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        MemoryStatCell(
                            label = "Free RAM",
                            value = "${memorySnapshot.availableRamMb} MB",
                            valueColor = animatedStatusColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MemoryStatCell(
                            label = "Resident (Used)",
                            value = "${memorySnapshot.usedRamMb} MB",
                            valueColor = colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        MemoryStatCell(
                            label = "Safety Limit",
                            value = "450 MB",
                            valueColor = colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Linear Progress Bar with Safety Threshold
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(4.dp))
                            .background(animatedStatusColor)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0 MB",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "Threshold: 450 MB OOM Guardrail",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${memorySnapshot.totalRamMb} MB",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hardware Recommendation Tier Banner (Google Settings Card)
            val (tierTitle, tierDesc) = when (memorySnapshot.tier) {
                MemoryTier.TIER_SUB_4GB -> Pair(
                    "Compact Tier (< 4GB RAM)",
                    "Recommended for ≤1B models (Qwen-0.5B, SmolLM). Ultra-low memory footprint."
                )
                MemoryTier.TIER_4GB_TO_6GB -> Pair(
                    "Balanced Tier (4 – 6GB RAM)",
                    "Optimal for 1B–3B models with hybrid mmap streaming active."
                )
                MemoryTier.TIER_8GB_TO_12GB -> Pair(
                    "High Performance Tier (8 – 12GB RAM)",
                    "Full-speed 7B–8B 4-bit (Q4_K_M) execution with fast KV cache."
                )
                MemoryTier.TIER_16GB_PLUS -> Pair(
                    "Workstation Tier (≥ 16GB RAM)",
                    "High capability for 14B–20B quantized models."
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = tierTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tierDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Warning banner if free RAM is under threshold
            AnimatedVisibility(visible = memorySnapshot.pressure != MemoryPressure.NORMAL) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (memorySnapshot.pressure == MemoryPressure.CRITICAL)
                                    "Safety Guardrail: Free RAM < 450 MB! Native inference will abort to prevent OOM crash."
                                else
                                    "Memory pressure detected. Active dynamic KV-cache pruning is engaged.",
                                color = colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryStatCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
