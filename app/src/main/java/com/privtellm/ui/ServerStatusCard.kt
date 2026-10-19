package com.privtellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.privtellm.server.LocalAiServerService
import com.privtellm.server.ServerState
import com.privtellm.server.ServerStatus
import com.privtellm.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ServerStatusCard(
    serverState: ServerState,
    onToggleServer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isRunning = serverState.status == ServerStatus.RUNNING
    val isStarting = serverState.status == ServerStatus.STARTING
    var showCopied by remember { mutableStateOf(false) }
    var showCopiedKey by remember { mutableStateOf(false) }
    var showCopiedWeb by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
                else if (isStarting)
                    listOf(AccentCyan.copy(alpha = 0.8f), colorScheme.outline.copy(alpha = 0.5f))
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
                            text = when {
                                isStarting -> "Initializing HTTP Engine..."
                                isRunning -> "Listening on http://0.0.0.0:${serverState.port}"
                                serverState.status == ServerStatus.ERROR -> "Server Error"
                                else -> "Service Offline"
                            },
                            color = when {
                                isStarting -> AccentCyan
                                isRunning -> AccentBlue
                                serverState.status == ServerStatus.ERROR -> StatusCritical
                                else -> colorScheme.onSurface.copy(alpha = 0.5f)
                            },
                            fontSize = 11.sp
                        )
                    }
                }

                // Switch with loading state
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AccentCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Switch(
                        checked = isRunning || isStarting,
                        onCheckedChange = { if (!isStarting) onToggleServer(it) },
                        enabled = !isStarting,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.4f),
                            uncheckedTrackColor = colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Error Alert Banner
            if (serverState.lastError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusCriticalGlow,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(StatusCritical, StatusCritical.copy(alpha = 0.4f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Error",
                            tint = StatusCritical,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = serverState.lastError ?: "",
                            color = StatusCritical,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LAN Endpoint Display Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val endpoint = "http://${serverState.localIp}:${serverState.port}"
                        copyToClipboard(context, "API Endpoint", endpoint)
                        showCopied = true
                        coroutineScope.launch {
                            delay(2000)
                            showCopied = false
                        }
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
                        color = if (showCopied) StatusSuccessGlow else colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (showCopied) StatusSuccess else colorScheme.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showCopied) "COPIED" else "COPY",
                                color = if (showCopied) StatusSuccess else colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // API Authentication Key Card
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        copyToClipboard(context, "API Key", serverState.apiKey)
                        showCopiedKey = true
                        coroutineScope.launch {
                            delay(2000)
                            showCopiedKey = false
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(AccentCyan.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.3f)))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "API Key (Bearer Auth Required for Web/Remote)",
                            color = colorScheme.onSurface.copy(alpha = 0.55f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = serverState.apiKey,
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val newKey = "sk-privtellm-${UUID.randomUUID().toString().take(8)}"
                                LocalAiServerService.setApiKey(newKey)
                                Toast.makeText(context, "New API Key generated", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate Key",
                                tint = colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (showCopiedKey) StatusSuccessGlow else colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (showCopiedKey) Icons.Default.Check else Icons.Default.Key,
                                    contentDescription = "Copy Key",
                                    tint = if (showCopiedKey) StatusSuccess else colorScheme.onSurface.copy(alpha = 0.65f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showCopiedKey) "COPIED" else "COPY",
                                    color = if (showCopiedKey) StatusSuccess else colorScheme.onSurface.copy(alpha = 0.65f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Web Chat Client URL Pill
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = AccentBlueGlow.copy(alpha = 0.4f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(AccentBlue.copy(alpha = 0.6f), AccentCyan.copy(alpha = 0.4f)))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Web Chat Client (Open from any browser)",
                            color = AccentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "http://${serverState.localIp}:${serverState.port}/",
                            color = colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row {
                        OutlinedButton(
                            onClick = {
                                val url = "http://${serverState.localIp}:${serverState.port}/"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    copyToClipboard(context, "Web Client URL", url)
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                val url = "http://${serverState.localIp}:${serverState.port}/"
                                copyToClipboard(context, "Web Client URL", url)
                                showCopiedWeb = true
                                coroutineScope.launch {
                                    delay(2000)
                                    showCopiedWeb = false
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (showCopiedWeb) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Link",
                                tint = if (showCopiedWeb) StatusSuccess else colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Served Model Status Bar
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (serverState.activeModel != null) AccentBlueGlow.copy(alpha = 0.6f) else colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Active Model",
                            tint = if (serverState.activeModel != null) AccentCyan else colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SERVED MODEL: ",
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = serverState.activeModel?.name ?: "None Loaded (Chat returns 503)",
                            color = if (serverState.activeModel != null) AccentCyan else StatusWarning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (serverState.activeModel != null) {
                        Text(
                            text = serverState.activeModel?.quantization ?: "",
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
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
                                      -H "Authorization: Bearer ${serverState.apiKey}" \
                                      -d '{"model":"local","messages":[{"role":"user","content":"Hello from anywhere!"}],"stream":true}'
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
                            onClick = { showRemoteDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remote Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    // Remote Worldwide Web Access Dialog
    if (showRemoteDialog) {
        AlertDialog(
            onDismissRequest = { showRemoteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Access from Anywhere in the World", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Because your Android phone is behind a home Wi-Fi router or mobile carrier NAT, public internet users from other countries cannot reach your phone IP directly without a tunnel.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Text(
                        text = "1. Cloudflare Quick Tunnel (Free, No account needed):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AccentCyan
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cmd = "cloudflared tunnel --url http://${serverState.localIp}:${serverState.port}"
                                copyToClipboard(context, "Cloudflare Tunnel Command", cmd)
                            }
                    ) {
                        Text(
                            text = "cloudflared tunnel --url http://${serverState.localIp}:${serverState.port}\n(Click to copy command)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = "2. Or with Localtunnel (Requires Node.js):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AccentCyan
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cmd = "npx localtunnel --port ${serverState.port}"
                                copyToClipboard(context, "Localtunnel Command", cmd)
                            }
                    ) {
                        Text(
                            text = "npx localtunnel --port ${serverState.port}\n(Click to copy command)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = "3. Share the generated public https:// link and your API Key (${serverState.apiKey}) with anyone! They can chat from any browser worldwide.",
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRemoteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Got It", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
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
