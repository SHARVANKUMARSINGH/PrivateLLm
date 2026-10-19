package com.privtellm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        animationSpec = tween(durationMillis = 600),
        label = "RamProgress"
    )

    val (statusColor, statusText) = when (memorySnapshot.pressure) {
        MemoryPressure.NORMAL -> Pair(AccentGreen, "NORMAL")
        MemoryPressure.MODERATE -> Pair(AccentAmber, "PRESSURE")
        MemoryPressure.CRITICAL -> Pair(AccentRed, "CRITICAL (<450MB)")
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(durationMillis = 400),
        label = "StatusColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(animatedStatusColor.copy(alpha = 0.5f), BorderColor)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "RAM Profiler",
                        tint = animatedStatusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HARDWARE RAM PROFILER",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = animatedStatusColor.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(animatedStatusColor, animatedStatusColor.copy(alpha = 0.4f)))
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
                                .background(animatedStatusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = animatedStatusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large RAM Numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Used RAM / Total",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${memorySnapshot.usedRamMb}",
                            color = TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = " / ${memorySnapshot.totalRamMb} MB",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Free Available",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${memorySnapshot.availableRamMb} MB",
                        color = animatedStatusColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar with 450 MB threshold indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(DarkSurfaceVariant)
            ) {
                // Used RAM bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentBlue, animatedStatusColor)
                            )
                        )
                )

                // Critical 450MB safety line mark
                val thresholdRatio = 1f - (450f / memorySnapshot.totalRamMb.coerceAtLeast(1024L))
                if (thresholdRatio in 0.1f..0.98f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .offset(x = (thresholdRatio * 320).dp) // approximate visual guide
                            .background(AccentRed)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware Recommendation Tier Banner
            val tierInfo = when (memorySnapshot.tier) {
                MemoryTier.TIER_SUB_4GB ->
                    "Tier: <4GB RAM • Recommended: ≤1B models (Qwen-0.5B, SmolLM)"
                MemoryTier.TIER_4GB_TO_6GB ->
                    "Tier: 4-6GB RAM • Recommended: 1B-3B models with hybrid streaming"
                MemoryTier.TIER_8GB_TO_12GB ->
                    "Tier: 8-12GB RAM • Recommended: 7B-8B models (4-bit Q4_K_M)"
                MemoryTier.TIER_16GB_PLUS ->
                    "Tier: ≥16GB RAM • Recommended: 14B-20B heavily quantized models"
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = DarkBackground
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ $tierInfo",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
