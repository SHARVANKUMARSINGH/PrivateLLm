package com.privtellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    // Pulse animation for active inference indicator
    val infiniteTransition = rememberInfiniteTransition(label = "PulseInference")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isRunning) listOf(AccentGreen, BorderColor) else listOf(BorderColor, BorderColor)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Server Status & Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server Status",
                        tint = if (isRunning) AccentGreen else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OPENAI API SERVER",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Switch
                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggleServer(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // IP Address & Endpoint
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val endpoint = "http://${serverState.localIp}:${serverState.port}"
                        copyToClipboard(context, "API Endpoint", endpoint)
                    },
                shape = RoundedCornerShape(10.dp),
                color = DarkBackground,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(BorderColor, BorderColor))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isRunning) "Listening on Local Network" else "Server Offline",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "http://${serverState.localIp}:${serverState.port}",
                            color = if (isRunning) AccentGreen else TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Endpoint",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics row: Requests, Tokens, Active Inference
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricItem(
                    label = "Requests",
                    value = "${serverState.totalRequests}"
                )

                MetricItem(
                    label = "Tokens Streamed",
                    value = "${serverState.totalTokens}"
                )

                // Active inference indicator
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Inference Engine",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (serverState.isInferring) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentGreen.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "STREAMING",
                                color = AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) AccentBlue else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRunning) "IDLE / READY" else "STOPPED",
                                color = if (isRunning) TextSecondary else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Quick curl snippet helper button
            AnimatedVisibility(visible = isRunning) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val curl = """
                                curl -X POST http://${serverState.localIp}:${serverState.port}/v1/chat/completions \
                                  -H "Content-Type: application/json" \
                                  -d '{"model":"local","messages":[{"role":"user","content":"Hello from LAN!"}],"stream":true}'
                            """.trimIndent()
                            copyToClipboard(context, "cURL Command", curl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentBlue
                        )
                    ) {
                        Text(
                            text = "📋 Copy OpenAI cURL Test Command",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp
        )
        Text(
            text = value,
            color = TextPrimary,
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
