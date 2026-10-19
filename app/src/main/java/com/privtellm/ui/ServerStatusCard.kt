package com.privtellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privtellm.hub.DownloadStatus
import com.privtellm.hub.ModelCatalog
import com.privtellm.hub.ModelItem
import com.privtellm.nativebridge.NativeLib
import com.privtellm.server.LocalAiServerService
import com.privtellm.server.ServerState
import com.privtellm.server.ServerStatus
import com.privtellm.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var showModelSelectDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "ServerRadar")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarAlpha"
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
            // Header Row: Console Title & Power Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isRunning) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "OpenAI Server",
                                tint = if (isRunning) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OpenAI API Server",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            if (isRunning) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primary.copy(alpha = radarPulse))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isStarting -> "Starting HTTP engine..."
                                isRunning -> "Online on port ${serverState.port}"
                                serverState.status == ServerStatus.ERROR -> "Server error occurred"
                                else -> "Service offline"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isStarting -> colorScheme.primary
                                isRunning -> colorScheme.primary
                                serverState.status == ServerStatus.ERROR -> colorScheme.error
                                else -> colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Switch(
                        checked = isRunning || isStarting,
                        onCheckedChange = { if (!isStarting) onToggleServer(it) },
                        enabled = !isStarting,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.onPrimary,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = colorScheme.outline,
                            uncheckedTrackColor = colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Error Alert Banner
            if (serverState.lastError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Error",
                            tint = colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = serverState.lastError ?: "",
                            color = colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRunning) "LAN Accessible Endpoint" else "Server Inactive",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "http://${serverState.localIp}:${serverState.port}",
                            color = if (isRunning) colorScheme.primary else colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = {
                            val endpoint = "http://${serverState.localIp}:${serverState.port}"
                            copyToClipboard(context, "API Endpoint", endpoint)
                            showCopied = true
                            coroutineScope.launch {
                                delay(2000)
                                showCopied = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showCopied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // API Authentication Key Card
            Spacer(modifier = Modifier.height(10.dp))
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
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OpenAI Bearer API Key",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = serverState.apiKey,
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val newKey = "sk-privtellm-${UUID.randomUUID().toString().replace("-", "")}"
                                LocalAiServerService.setApiKey(newKey)
                                Toast.makeText(context, "New API Key generated", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate Key",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        FilledTonalButton(
                            onClick = {
                                copyToClipboard(context, "API Key", serverState.apiKey)
                                showCopiedKey = true
                                coroutineScope.launch {
                                    delay(2000)
                                    showCopiedKey = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (showCopiedKey) Icons.Default.Check else Icons.Default.Key,
                                contentDescription = "Copy Key",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showCopiedKey) "Copied" else "Copy",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Web Chat Client URL Pill
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.secondaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Web Chat Client (Open from any browser)",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "http://${serverState.localIp}:${serverState.port}/",
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row {
                        FilledTonalButton(
                            onClick = {
                                val url = "http://${serverState.localIp}:${serverState.port}/"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    copyToClipboard(context, "Web Client URL", url)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", style = MaterialTheme.typography.labelSmall)
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (showCopiedWeb) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Link",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Served Model Status Bar (Clean Vertical Stack to eliminate text collisions)
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE SERVING MODEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = serverState.activeModel?.name ?: "None Loaded (Chat returns 503)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (serverState.activeModel != null) colorScheme.primary else colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (serverState.activeModel != null) {
                            Text(
                                text = "${serverState.activeModel.quantization} • ${serverState.activeModel.parametersBillion}B params",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = { showModelSelectDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Switch", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-time Telemetry Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ServerMetricTile(
                    label = "Total Requests",
                    value = "${serverState.totalRequests}",
                    modifier = Modifier.weight(1f)
                )

                ServerMetricTile(
                    label = "Tokens Streamed",
                    value = "${serverState.totalTokens}",
                    modifier = Modifier.weight(1f)
                )

                // Engine State Tile
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Engine State",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (serverState.isInferring) colorScheme.primary
                                        else if (isRunning) colorScheme.tertiary
                                        else colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (serverState.isInferring) "Streaming" else if (isRunning) "Idle" else "Stopped",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (serverState.isInferring) colorScheme.primary else if (isRunning) colorScheme.tertiary else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Actions & Remote Access
            AnimatedVisibility(visible = isRunning) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy cURL", style = MaterialTheme.typography.labelLarge)
                        }

                        Button(
                            onClick = { showRemoteDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remote Access", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    // Remote Worldwide Web Access Dialog (Material 3 Google Style)
    if (showRemoteDialog) {
        AlertDialog(
            onDismissRequest = { showRemoteDialog = false },
            icon = {
                Icon(Icons.Default.Public, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    text = "Worldwide Remote Access",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Because your Android phone is behind home Wi-Fi or carrier NAT, remote users need a secure tunnel to connect:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Cloudflare Quick Tunnel (Free, instant):",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
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
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = "Share the generated public https:// link and your API Key (${serverState.apiKey}) with anyone! They can chat from any browser worldwide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRemoteDialog = false },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Got It", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Model Selection Modal Dialog (Material 3 Google Style)
    if (showModelSelectDialog) {
        val modelDir = ModelCatalog.getInternalModelDir(context)
        val installedFiles: List<java.io.File> = remember {
            modelDir.listFiles { _, name -> name.endsWith(".gguf", ignoreCase = true) }?.toList() ?: emptyList()
        }

        AlertDialog(
            onDismissRequest = { showModelSelectDialog = false },
            icon = {
                Icon(Icons.Default.Dns, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Select Model for Server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose which local model the OpenAI server should route API completions to:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )

                    if (installedFiles.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No .gguf models found in storage. Go to the Models tab to download or import a GGUF model.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        installedFiles.forEach { file ->
                            val isSelected = serverState.activeModel?.id == file.nameWithoutExtension
                            Surface(
                                onClick = {
                                    showModelSelectDialog = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            NativeLib.releaseModelSafe()
                                            val ok = NativeLib.initModelSafe(
                                                path = file.absolutePath,
                                                nThreads = 4,
                                                nGpuLayers = 0,
                                                mmap = true,
                                                streamingExecution = true
                                            )
                                            if (ok) {
                                                val matching = ModelCatalog.CURATED_MODELS.find { it.id == file.nameWithoutExtension }
                                                val model = matching ?: ModelItem(
                                                    id = file.nameWithoutExtension,
                                                    name = file.nameWithoutExtension.replace("_", " ").replace("-", " "),
                                                    description = "Active model",
                                                    parametersBillion = 1.0f,
                                                    quantization = "GGUF",
                                                    sizeBytes = file.length(),
                                                    downloadUrl = "",
                                                    status = DownloadStatus.LOADED,
                                                    localFilePath = file.absolutePath
                                                )
                                                LocalAiServerService.setActiveModel(model)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Server loaded ${model.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                border = if (isSelected) BorderStroke(1.dp, colorScheme.primary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.nameWithoutExtension,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${file.length() / (1024 * 1024)} MB GGUF",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = colorScheme.primary
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = colorScheme.onPrimary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelSelectDialog = false }) {
                    Text("Close", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun ServerMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
