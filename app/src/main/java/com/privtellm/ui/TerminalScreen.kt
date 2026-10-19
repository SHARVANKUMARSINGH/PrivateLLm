package com.privtellm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class TerminalEntry(
    val directory: String,
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var currentDir by remember { mutableStateOf(context.filesDir) }
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    val entries = remember { mutableStateListOf<TerminalEntry>() }

    // Quick Command Templates
    val quickCommands = listOf(
        "ls -lah",
        "pwd",
        "df -h",
        "free -m",
        "uname -a",
        "cat /proc/meminfo | head -n 6",
        "curl -s http://127.0.0.1:8080/v1/models",
        "netstat -tlpn"
    )

    fun executeCommand(rawCmd: String) {
        val trimmed = rawCmd.trim()
        if (trimmed.isEmpty() || isExecuting) return

        commandInput = ""
        isExecuting = true

        coroutineScope.launch {
            val (output, exitCode, newDir) = withContext(Dispatchers.IO) {
                try {
                    if (trimmed.startsWith("cd ") || trimmed == "cd") {
                        val target = trimmed.removePrefix("cd").trim()
                        val nextDir = when {
                            target.isEmpty() || target == "~" -> context.filesDir
                            target.startsWith("/") -> File(target)
                            target == ".." -> currentDir.parentFile ?: currentDir
                            else -> File(currentDir, target)
                        }
                        if (nextDir.exists() && nextDir.isDirectory) {
                            Triple("", 0, nextDir.canonicalFile)
                        } else {
                            Triple("cd: no such file or directory: $target\n", 1, currentDir)
                        }
                    } else if (trimmed == "clear") {
                        Triple("__CLEAR__", 0, currentDir)
                    } else {
                        val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed)
                        pb.directory(currentDir)
                        pb.redirectErrorStream(true)
                        val env = pb.environment()
                        env["PATH"] = (env["PATH"] ?: "") + ":/system/bin:/system/xbin:/vendor/bin"
                        env["HOME"] = context.filesDir.absolutePath

                        val process = pb.start()
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val sb = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            sb.append(line).append("\n")
                        }
                        val exit = process.waitFor()
                        Triple(sb.toString().ifEmpty { "(No output)\n" }, exit, currentDir)
                    }
                } catch (e: Exception) {
                    Triple("Execution error: ${e.message}\n", -1, currentDir)
                }
            }

            if (output == "__CLEAR__") {
                entries.clear()
            } else {
                entries.add(
                    TerminalEntry(
                        directory = currentDir.absolutePath,
                        command = trimmed,
                        output = output,
                        exitCode = exitCode
                    )
                )
            }
            currentDir = newDir
            isExecuting = false

            if (entries.isNotEmpty()) {
                listState.animateScrollToItem(entries.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Terminal Header Bar (Google Cloud Shell Style)
        Surface(
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Cloud Shell",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Terminal / Shell",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentDir.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = { entries.clear() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Terminal",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Quick Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCommands.forEach { cmd ->
                Surface(
                    onClick = { executeCommand(cmd) },
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Text(
                        text = cmd,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Divider(color = colorScheme.outlineVariant, thickness = 1.dp)

        // Terminal Output Log
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entries.isEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "PrivteLLM On-Device Shell Environment",
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Direct /system/bin/sh sandbox execution.\nType shell commands or tap the quick action chips above.",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                items(entries) { entry ->
                    Column {
                        // Prompt Line
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "privtellm@android",
                                color = colorScheme.tertiary,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ":",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = entry.directory.replace(context.filesDir.absolutePath, "~"),
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$ ",
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.command,
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Command Output
                        Text(
                            text = entry.output.trimEnd(),
                            color = if (entry.exitCode == 0) colorScheme.onSurface else colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Input Field Bar
        Surface(
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    color = colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                TextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = {
                        Text(
                            text = "Enter shell command...",
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceVariant,
                        unfocusedContainerColor = colorScheme.surfaceVariant,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { executeCommand(commandInput) })
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = { executeCommand(commandInput) },
                    enabled = commandInput.isNotBlank() && !isExecuting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    )
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            color = colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Execute Command",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
