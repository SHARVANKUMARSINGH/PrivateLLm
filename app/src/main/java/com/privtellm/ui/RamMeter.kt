package com.privtellm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
import com.privtellm.ui.theme.*

@Composable
fun RamMeter(
    memorySnapshot: MemorySnapshot,
    modifier: Modifier = Modifier
) {
    val usedRatio = memorySnapshot.usedPercentage.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = usedRatio,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "RamProgress"
    )

    val (statusColor, statusText, statusBg) = when (memorySnapshot.pressure) {
        MemoryPressure.NORMAL -> Triple(NeonGreen, "STABLE", NeonGreenGlow)
        MemoryPressure.MODERATE -> Triple(AmberAccent, "HIGH PRESSURE", Color(0x33F59E0B))
        MemoryPressure.CRITICAL -> Triple(CrimsonRed, "CRITICAL (<450MB)", CrimsonGlow)
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(durationMillis = 400),
        label = "StatusColor"
    )

    // Pulse animation for critical memory
    val infiniteTransition = rememberInfiniteTransition(label = "RamPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CriticalPulse"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(
                    animatedStatusColor.copy(alpha = if (memorySnapshot.pressure == MemoryPressure.CRITICAL) pulseAlpha else 0.6f),
                    SpaceCardBorder
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Telemetry Icon & Title & Pressure Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(animatedStatusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "RAM Profiler",
                            tint = animatedStatusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "HARDWARE PROFILER",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Real-time RAM & OOM Guardrail",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Dynamic Status Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusBg,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(animatedStatusColor, animatedStatusColor.copy(alpha = 0.4f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    animatedStatusColor.copy(
                                        alpha = if (memorySnapshot.pressure == MemoryPressure.CRITICAL) pulseAlpha else 1f
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = animatedStatusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gauge & Memory Numbers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Semi-Arc Meter
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 8.dp.toPx()
                        // Background Track
                        drawArc(
                            color = SpaceSurfaceVariant,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Progress Arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(ElectricCyan, animatedStatusColor)
                            ),
                            startAngle = 135f,
                            sweepAngle = 270f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(usedRatio * 100).toInt()}%",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "USED",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Telemetry Metrics Grid
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total RAM", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "${String.format("%.1f", memorySnapshot.totalRamGb)} GB",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Free RAM", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "${memorySnapshot.availableRamMb} MB",
                                color = animatedStatusColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Resident (Used)", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "${memorySnapshot.usedRamMb} MB",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Safety Limit", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "450 MB",
                                color = CrimsonRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Linear Progress Bar with 450 MB Threshold Mark
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SpaceSurfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ElectricCyan, animatedStatusColor)
                                )
                            )
                    )

                    // 450 MB Safety line visual marker
                    val safetyRatio = (1f - (450f / memorySnapshot.totalRamMb.coerceAtLeast(1024L))).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = (safetyRatio * 280).dp)
                            .background(CrimsonRed)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 MB", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("▲ 450MB Safety Guardrail", color = CrimsonRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${memorySnapshot.totalRamMb} MB", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware Recommendation Tier Banner
            val (tierTitle, tierDesc, tierIconColor) = when (memorySnapshot.tier) {
                MemoryTier.TIER_SUB_4GB -> Triple(
                    "Compact Tier (< 4GB RAM)",
                    "Best for ≤1B models (Qwen-0.5B, SmolLM). Low footprint.",
                    ElectricCyan
                )
                MemoryTier.TIER_4GB_TO_6GB -> Triple(
                    "Balanced Tier (4 – 6GB RAM)",
                    "Optimal for 1B–3B models with hybrid mmap streaming active.",
                    NeonGreen
                )
                MemoryTier.TIER_8GB_TO_12GB -> Triple(
                    "High Performance Tier (8 – 12GB RAM)",
                    "Full-speed 7B–8B 4-bit (Q4_K_M) execution with fast KV cache.",
                    PurpleAccent
                )
                MemoryTier.TIER_16GB_PLUS -> Triple(
                    "Workstation Tier (≥ 16GB RAM)",
                    "High reasoning capacity for 14B–20B quantized models.",
                    AmberAccent
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = SpaceBackground,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(tierIconColor.copy(alpha = 0.4f), SpaceCardBorder))
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(tierIconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = tierIconColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = tierTitle,
                            color = tierIconColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tierDesc,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Warning banner if free RAM is tight
            AnimatedVisibility(visible = memorySnapshot.pressure != MemoryPressure.NORMAL) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = animatedStatusColor.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = animatedStatusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (memorySnapshot.pressure == MemoryPressure.CRITICAL)
                                    "Safety Guardrail: Free RAM < 450 MB! Native inference will abort to prevent OOM crash."
                                else
                                    "Memory pressure detected. Active dynamic KV-cache pruning is engaged.",
                                color = animatedStatusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
