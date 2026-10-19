package com.privtellm.ui

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
import com.privtellm.ui.theme.AccentBlue
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
            .background(Color(0xFF06090F))
    ) {
        // Terminal Header Bar
        Surface(
            color = Color(0xFF0D1117),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF21262D))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Termux Console",
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PrivteLLM Shell / Termux Console",
                            color = Color(0xFFE6EDF3),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = currentDir.absolutePath,
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
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
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Console",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { cmd ->
                Surface(
                    onClick = { executeCommand(cmd) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF161B22),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF30363D)))
                ) {
                    Text(
                        text = cmd,
                        color = Color(0xFF58A6FF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Divider(color = Color(0xFF21262D), thickness = 1.dp)

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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entries.isEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "PrivteLLM Linux Environment",
                                color = Color(0xFF3FB950),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Built-in /system/bin/sh execution environment.\nType shell commands or tap the quick action chips above.",
                                color = Color(0xFF8B949E),
                                fontSize = 12.sp,
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
                                color = Color(0xFF3FB950),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ":",
                                color = Color(0xFF8B949E),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = entry.directory.replace(context.filesDir.absolutePath, "~"),
                                color = Color(0xFF58A6FF),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$ ",
                                color = Color(0xFFE6EDF3),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.command,
                                color = Color(0xFFF0F6FC),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Command Output
                        Text(
                            text = entry.output.trimEnd(),
                            color = if (entry.exitCode == 0) Color(0xFFC9D1D9) else Color(0xFFFFA198),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Input Field Bar
        Surface(
            color = Color(0xFF0D1117),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF21262D))),
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
                    color = Color(0xFF3FB950),
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
                            color = Color(0xFF8B949E),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF161B22),
                        unfocusedContainerColor = Color(0xFF161B22),
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { executeCommand(commandInput) })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { executeCommand(commandInput) },
                    enabled = commandInput.isNotBlank() && !isExecuting,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (commandInput.isNotBlank() && !isExecuting) AccentBlue else Color(0xFF21262D),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Execute Command",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
