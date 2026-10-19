package com.privtellm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.privtellm.PrivteLLMApp
import com.privtellm.core.profiler.DeviceProfiler
import com.privtellm.core.profiler.MemorySnapshot
import com.privtellm.hub.ModelCatalog
import com.privtellm.hub.ModelDownloader
import com.privtellm.hub.ModelItem
import com.privtellm.nativebridge.NativeLib
import com.privtellm.server.LocalAiServerService
import com.privtellm.server.ServerState
import com.privtellm.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String) {
    SERVER("Dashboard"),
    MODELS("Models"),
    CHAT("Playground"),
    STUDIO("Studio"),
    TERMINAL("Terminal")
}


class MainActivity : ComponentActivity() {

    private lateinit var profiler: DeviceProfiler
    private lateinit var downloader: ModelDownloader

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        profiler = (application as PrivteLLMApp).profiler
        downloader = (application as PrivteLLMApp).downloader

        checkNotificationPermission()

        setContent {
            PrivteLLMTheme {
                MainAppScreen(
                    profiler = profiler,
                    downloader = downloader,
                    onStartServer = { LocalAiServerService.start(this) },
                    onStopServer = { LocalAiServerService.stop(this) }
                )
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    profiler: DeviceProfiler,
    downloader: ModelDownloader,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableStateOf(MainTab.SERVER) }
    val memorySnapshot by profiler.monitorMemory(intervalMs = 1000)
        .collectAsState(initial = profiler.getMemorySnapshot())

    val serverState by LocalAiServerService.serverState.collectAsState()
    var loadedModel by remember { mutableStateOf<ModelItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PrivteLLM",
                                color = AccentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentBlueGlow
                            ) {
                                Text(
                                    text = "v1.0.0",
                                    color = AccentBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Memory Header Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.surfaceVariant,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.5f), colorScheme.outline.copy(alpha = 0.5f)))
                            ),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (memorySnapshot.availableRamMb < 450) StatusCritical else AccentBlue)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${memorySnapshot.availableRamMb} MB Free",
                                    color = colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = colorScheme.surface,
                contentColor = colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.SERVER,
                    onClick = { selectedTab = MainTab.SERVER },
                    icon = { Icon(Icons.Default.Dns, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        unselectedTextColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        indicatorColor = AccentBlueGlow
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.MODELS,
                    onClick = { selectedTab = MainTab.MODELS },
                    icon = { Icon(Icons.Default.Store, contentDescription = "Model Store") },
                    label = { Text("Model Store", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        unselectedTextColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        indicatorColor = AccentBlueGlow
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.CHAT,
                    onClick = { selectedTab = MainTab.CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Playground") },
                    label = { Text("Playground", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        unselectedTextColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        indicatorColor = AccentBlueGlow
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.STUDIO,
                    onClick = { selectedTab = MainTab.STUDIO },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Studio") },
                    label = { Text("Studio", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        unselectedTextColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        indicatorColor = AccentBlueGlow
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.TERMINAL,
                    onClick = { selectedTab = MainTab.TERMINAL },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                    label = { Text("Terminal", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        unselectedTextColor = colorScheme.onSurface.copy(alpha = 0.55f),
                        indicatorColor = AccentBlueGlow
                    )
                )
            }
        }

    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colorScheme.background)
        ) {
            when (selectedTab) {
                MainTab.SERVER -> {
                    DashboardScreen(
                        memorySnapshot = memorySnapshot,
                        serverState = serverState,
                        loadedModel = loadedModel,
                        onToggleServer = { shouldStart ->
                            if (shouldStart) onStartServer() else onStopServer()
                        },
                        onNavigateToModels = { selectedTab = MainTab.MODELS },
                        onNavigateToChat = { selectedTab = MainTab.CHAT },
                        onUnloadModel = {
                            coroutineScope.launch(Dispatchers.IO) {
                                NativeLib.releaseModelSafe()
                                loadedModel = null
                                LocalAiServerService.setActiveModel(null)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Model unloaded from RAM", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                MainTab.MODELS -> {
                    ModelCatalogScreen(
                        models = ModelCatalog.CURATED_MODELS,
                        profiler = profiler,
                        downloader = downloader,
                        loadedModelId = loadedModel?.id,
                        onLoadModel = { modelToLoad ->
                            coroutineScope.launch {
                                val targetFile = downloader.getTargetFile(modelToLoad)
                                if (targetFile.exists()) {
                                    downloader.setModelLoading(modelToLoad, true)
                                    try {
                                        val success = withContext(Dispatchers.IO) {
                                            NativeLib.initModelSafe(
                                                path = targetFile.absolutePath,
                                                nThreads = 4,
                                                nGpuLayers = 0,
                                                mmap = true,
                                                streamingExecution = true
                                            )
                                        }
                                        if (success) {
                                            loadedModel = modelToLoad
                                            LocalAiServerService.setActiveModel(modelToLoad)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Loaded ${modelToLoad.name} into RAM (Ready)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Failed to initialize model. Please ensure enough free RAM is available.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "Error loading model: ${t.localizedMessage ?: t.javaClass.simpleName}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } finally {
                                        downloader.setModelLoading(modelToLoad, false)
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Model file not downloaded yet. Please download it first.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onUnloadModel = {
                            coroutineScope.launch(Dispatchers.IO) {
                                NativeLib.releaseModelSafe()
                                loadedModel = null
                                LocalAiServerService.setActiveModel(null)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Model unloaded from RAM", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onNavigateToChat = {
                            selectedTab = MainTab.CHAT
                        }
                    )
                }

                MainTab.CHAT -> {
                    ChatPlaygroundScreen(
                        loadedModel = loadedModel
                    )
                }

                MainTab.STUDIO -> {
                    StudioScreen()
                }

                MainTab.TERMINAL -> {
                    TerminalScreen()
                }
            }
        }
    }
}


@Composable
fun DashboardScreen(
    memorySnapshot: MemorySnapshot,
    serverState: ServerState,
    loadedModel: ModelItem?,
    onToggleServer: (Boolean) -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToChat: () -> Unit,
    onUnloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hardware RAM Meter HUD
        RamMeter(memorySnapshot = memorySnapshot)

        // 2. OpenAI Local Server Console
        ServerStatusCard(
            serverState = serverState,
            onToggleServer = onToggleServer
        )

        // 3. Active Loaded Model Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    if (loadedModel != null)
                        listOf(AccentBlue.copy(alpha = 0.6f), colorScheme.outline.copy(alpha = 0.4f))
                    else
                        listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
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
                                .background(if (loadedModel != null) AccentBlueGlow else colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Active Engine",
                                tint = if (loadedModel != null) AccentBlue else colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "ACTIVE INFERENCE ENGINE",
                                color = colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (loadedModel != null) "C++20 NDK Tensor Pipeline Active" else "No Weights Loaded in RAM",
                                color = if (loadedModel != null) AccentBlue else colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (loadedModel != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentBlueGlow
                        ) {
                            Text(
                                text = "PRIME",
                                color = AccentBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (loadedModel != null) {
                    Text(
                        text = loadedModel.name,
                        color = colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${loadedModel.parametersBillion}B params • ${loadedModel.quantization} • ${loadedModel.contextLength} context window • ${loadedModel.sizeFormatted}",
                        color = colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onNavigateToChat,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Chat", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onUnloadModel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarning),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(StatusWarning.copy(alpha = 0.6f), StatusWarning.copy(alpha = 0.2f)))
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Unload RAM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "Native RAM is currently cleared. Download and load a quantized model from the Model Store to start local inference.",
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToModels,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse Curated Models", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Runtime Specifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(colorScheme.outline.copy(alpha = 0.4f), colorScheme.outline.copy(alpha = 0.4f)))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "RUNTIME & ACCELERATION SPECS",
                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecItem(title = "NATIVE ABI", value = "arm64-v8a NEON")
                    SpecItem(title = "C++ ENGINE", value = "C++20 NDK 26.x")
                    SpecItem(title = "PARALLELISM", value = "OpenMP Multi-Core")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecItem(title = "GPU BACKEND", value = "Vulkan GGML")
                    SpecItem(title = "MEMORY STREAM", value = "Mmap Layer Streamer")
                    SpecItem(title = "SAFETY LIMIT", value = "450 MB Active LMK")
                }
            }
        }
    }
}

@Composable
private fun SpecItem(title: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Text(title, color = colorScheme.onSurface.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}
