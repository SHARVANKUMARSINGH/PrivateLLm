package com.privtellm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
    CHAT("Chat"),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Google Brand Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "PrivteLLM",
                                style = MaterialTheme.typography.titleLarge,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "v1.2.0",
                                    color = colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Google Style Memory Status Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
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
                                            if (memorySnapshot.availableRamMb < 450) colorScheme.error
                                            else colorScheme.primary
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${memorySnapshot.availableRamMb} MB Free",
                                    color = colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.SERVER,
                    onClick = { selectedTab = MainTab.SERVER },
                    icon = { Icon(Icons.Default.Dns, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onSecondaryContainer,
                        selectedTextColor = colorScheme.onSurface,
                        indicatorColor = colorScheme.secondaryContainer,
                        unselectedIconColor = colorScheme.onSurfaceVariant,
                        unselectedTextColor = colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.MODELS,
                    onClick = { selectedTab = MainTab.MODELS },
                    icon = { Icon(Icons.Default.Store, contentDescription = "Models") },
                    label = { Text("Models", style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onSecondaryContainer,
                        selectedTextColor = colorScheme.onSurface,
                        indicatorColor = colorScheme.secondaryContainer,
                        unselectedIconColor = colorScheme.onSurfaceVariant,
                        unselectedTextColor = colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.CHAT,
                    onClick = { selectedTab = MainTab.CHAT },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat") },
                    label = { Text("Chat", style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onSecondaryContainer,
                        selectedTextColor = colorScheme.onSurface,
                        indicatorColor = colorScheme.secondaryContainer,
                        unselectedIconColor = colorScheme.onSurfaceVariant,
                        unselectedTextColor = colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.STUDIO,
                    onClick = { selectedTab = MainTab.STUDIO },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Studio") },
                    label = { Text("Studio", style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onSecondaryContainer,
                        selectedTextColor = colorScheme.onSurface,
                        indicatorColor = colorScheme.secondaryContainer,
                        unselectedIconColor = colorScheme.onSurfaceVariant,
                        unselectedTextColor = colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.TERMINAL,
                    onClick = { selectedTab = MainTab.TERMINAL },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                    label = { Text("Terminal", style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onSecondaryContainer,
                        selectedTextColor = colorScheme.onSurface,
                        indicatorColor = colorScheme.secondaryContainer,
                        unselectedIconColor = colorScheme.onSurfaceVariant,
                        unselectedTextColor = colorScheme.onSurfaceVariant
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
                        models = ModelCatalog.getAllModels(context),
                        profiler = profiler,
                        downloader = downloader,
                        loadedModelId = loadedModel?.id,
                        onLoadModel = { modelToLoad ->
                            coroutineScope.launch {
                                if (modelToLoad.capability == com.privtellm.hub.ModelCapability.TEXT_TO_SPEECH) {
                                    loadedModel = modelToLoad
                                    selectedTab = MainTab.STUDIO
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Activated ${modelToLoad.name} for Studio TTS", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }
                                if (modelToLoad.capability == com.privtellm.hub.ModelCapability.IMAGE_GENERATION) {
                                    loadedModel = modelToLoad
                                    selectedTab = MainTab.STUDIO
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Activated ${modelToLoad.name} for Image Studio", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }

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
                        },
                        onNavigateToStudio = {
                            selectedTab = MainTab.STUDIO
                        }
                    )
                }

                MainTab.CHAT -> {
                    ChatPlaygroundScreen(
                        loadedModel = loadedModel
                    )
                }

                MainTab.STUDIO -> {
                    StudioScreen(activeModel = loadedModel)
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
        // 1. Hardware RAM Meter
        RamMeter(memorySnapshot = memorySnapshot)

        // 2. OpenAI Local Server Console
        ServerStatusCard(
            serverState = serverState,
            onToggleServer = onToggleServer
        )

        // 3. Active Loaded Model Card (Google Card Style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (loadedModel != null) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Active Engine",
                                    tint = if (loadedModel != null) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Active Inference Engine",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (loadedModel != null) "llama.cpp C++20 NDK Pipeline Active" else "No Weights Loaded in RAM",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (loadedModel != null) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (loadedModel != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "LOADED",
                                color = colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (loadedModel != null) {
                    Text(
                        text = loadedModel.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${loadedModel.parametersBillion}B parameters • ${loadedModel.quantization} • ${loadedModel.contextLength} tokens • ${loadedModel.sizeFormatted}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToChat,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Chat", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onUnloadModel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("Unload RAM", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Text(
                        text = "Native RAM is currently cleared. Download and load a quantized model from the Models tab to start local inference.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    FilledTonalButton(
                        onClick = onNavigateToModels,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse Models", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // 4. Runtime Specifications Card (Google Pixel Settings Style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "System Acceleration Specs",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecItem(title = "NATIVE ARCH", value = "arm64-v8a NEON")
                    SpecItem(title = "COMPILER", value = "C++20 NDK 26.x")
                    SpecItem(title = "VECTOR ENGINE", value = "OpenMP Multi-Core")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecItem(title = "TENSOR BACKEND", value = "GGML Vectorized")
                    SpecItem(title = "RAM PIPELINE", value = "Mmap Layer Streamer")
                    SpecItem(title = "SAFETY LIMIT", value = "450 MB Active LMK")
                }
            }
        }
    }
}

@Composable
private fun SpecItem(title: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.widthIn(min = 96.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
