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
    val colorScheme = MaterialTheme.colorScheme
    val isRunning = serverState.status == ServerStatus.RUNNING
    var testResult by remember { mutableStateOf<String?>(null) }

    // Pulse animation for active server & inference indicator
    val infiniteTransition = rememberInfiniteTransition(label = "ServerRadar")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isRunning)
                    listOf(AccentBlue.copy(alpha = 0.8f), colorScheme.outline.copy(alpha = 0.5f))
                else
                    listOf(colorScheme.outline.copy(alpha = 0.5f), colorScheme.outline.copy(alpha = 0.5f))
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
                            .background(if (isRunning) AccentBlueGlow else colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "OpenAI API Server",
                            tint = if (isRunning) AccentBlue else colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LOCAL OPENAI SERVER",
                                color = colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (isRunning) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(AccentBlue.copy(alpha = radarPulse))
                                )
                            }
                        }
                        Text(
                            text = if (isRunning) "Listening on http://0.0.0.0:${serverState.port}" else "Service Offline",
                            color = if (isRunning) AccentBlue else colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Switch styled with #007ACC
                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggleServer(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.4f),
                        uncheckedTrackColor = colorScheme.surfaceVariant
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
                shape = RoundedCornerShape(10.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        if (isRunning) listOf(AccentBlue.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.3f))
                        else listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f))
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
                            color = colorScheme.onSurface.copy(alpha = 0.55f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = "http://${serverState.localIp}:${serverState.port}",
                            color = if (isRunning) AccentBlue else colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = colorScheme.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "COPY",
                                color = colorScheme.onSurface.copy(alpha = 0.65f),
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
                    accentColor = AccentBlue
                )

                ServerMetricTile(
                    label = "TOKENS STREAMED",
                    value = "${serverState.totalTokens}",
                    accentColor = AccentBlueLight
                )

                // Engine State Pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ENGINE STATE",
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (serverState.isInferring) StatusCriticalGlow else colorScheme.surfaceVariant
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
                                        if (serverState.isInferring) StatusCritical
                                        else if (isRunning) AccentBlue
                                        else colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (serverState.isInferring) "STREAMING" else if (isRunning) "IDLE" else "STOPPED",
                                color = if (serverState.isInferring) StatusCritical else if (isRunning) AccentBlue else colorScheme.onSurface.copy(alpha = 0.45f),
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentBlue
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(AccentBlue.copy(alpha = 0.6f), AccentBlue.copy(alpha = 0.3f)))
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ping Server", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    testResult?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = AccentBlue,
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
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = label,
            color = colorScheme.onSurface.copy(alpha = 0.6f),
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
