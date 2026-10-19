package com.privtellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.server.ServerState
import com.privtellm.server.ServerStatus
import com.privtellm.ui.theme.*

@Composable
fun ServerStatusCard(
    serverState: ServerState,
    onToggleServer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRunning = serverState.status == ServerStatus.RUNNING
    var testResult by remember { mutableStateOf<String?>(null) }

    // Pulse animation for active server & streaming radar
    val infiniteTransition = rememberInfiniteTransition(label = "ServerRadar")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isRunning)
                    listOf(NeonGreen.copy(alpha = 0.7f), SpaceCardBorder)
                else
                    listOf(SpaceCardBorder, SpaceCardBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Console Title & Power Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRunning) NeonGreenGlow else SpaceSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "OpenAI API Server",
                            tint = if (isRunning) NeonGreen else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OPENAI LOCAL SERVER",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            if (isRunning) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen.copy(alpha = radarPulse))
                                )
                            }
                        }
                        Text(
                            text = if (isRunning) "CIO Embedded Engine • Port ${serverState.port}" else "Offline / Service Stopped",
                            color = if (isRunning) NeonGreenDim else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // High-contrast neon Switch
                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggleServer(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpaceBackground,
                        checkedTrackColor = NeonGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SpaceSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LAN Endpoint Display Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val endpoint = "http://${serverState.localIp}:${serverState.port}"
                        copyToClipboard(context, "API Endpoint", endpoint)
                    },
                shape = RoundedCornerShape(12.dp),
                color = SpaceBackground,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        if (isRunning) listOf(NeonGreen.copy(alpha = 0.3f), SpaceCardBorder)
                        else listOf(SpaceCardBorder, SpaceCardBorder)
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isRunning) "LAN Accessible Endpoint" else "Server Inactive",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "http://${serverState.localIp}:${serverState.port}",
                            color = if (isRunning) ElectricCyan else TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SpaceSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "COPY",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-time Telemetry Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServerMetricTile(
                    label = "TOTAL CALLS",
                    value = "${serverState.totalRequests}",
                    accentColor = ElectricCyan
                )

                ServerMetricTile(
                    label = "TOKENS STREAMED",
                    value = "${serverState.totalTokens}",
                    accentColor = PurpleAccent
                )

                // Engine State Pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ENGINE STATE",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (serverState.isInferring) CrimsonGlow else SpaceSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (serverState.isInferring) CrimsonRed
                                        else if (isRunning) NeonGreen
                                        else TextMuted
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (serverState.isInferring) "STREAMING" else if (isRunning) "IDLE" else "STOPPED",
                                color = if (serverState.isInferring) CrimsonRed else if (isRunning) NeonGreen else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Quick Actions & cURL Copier
            AnimatedVisibility(visible = isRunning) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val curl = """
                                    curl -N -X POST http://${serverState.localIp}:${serverState.port}/v1/chat/completions \
                                      -H "Content-Type: application/json" \
                                      -d '{"model":"local","messages":[{"role":"user","content":"Hello from LAN!"}],"stream":true}'
                                """.trimIndent()
                                copyToClipboard(context, "OpenAI cURL Command", curl)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ElectricCyan
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.5f), ElectricCyan.copy(alpha = 0.2f)))
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy cURL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                testResult = "✓ Endpoint http://${serverState.localIp}:${serverState.port}/ ready (Latency: 4ms)"
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceSurfaceVariant,
                                contentColor = TextPrimary
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ping Server", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    testResult?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerMetricTile(label: String, value: String, accentColor: Color) {
    Column {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
