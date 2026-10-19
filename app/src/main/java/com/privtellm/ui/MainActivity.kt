package com.privtellm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.privtellm.core.profiler.DeviceProfiler
import com.privtellm.core.profiler.MemorySnapshot
import com.privtellm.core.profiler.MemoryTier
import com.privtellm.hub.DownloadStatus
import com.privtellm.hub.ModelCatalog
import com.privtellm.hub.ModelDownloader
import com.privtellm.hub.ModelItem
import com.privtellm.nativebridge.NativeLib
import com.privtellm.server.LocalAiServerService
import com.privtellm.server.ServerStatus
import com.privtellm.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String) {
    SERVER("Dashboard"),
    MODELS("Model Store"),
    CHAT("Playground")
}

class MainActivity : ComponentActivity() {

    private lateinit var profiler: DeviceProfiler
    private lateinit var downloader: ModelDownloader

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Notification permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        profiler = DeviceProfiler(this)
        downloader = ModelDownloader(this)

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
    var selectedTab by remember { mutableStateOf(MainTab.SERVER) }
    val memorySnapshot by profiler.monitorMemory(intervalMs = 1200)
        .collectAsState(initial = profiler.getMemorySnapshot())

    val serverState by LocalAiServerService.serverState.collectAsState()
    var loadedModel by remember { mutableStateOf<ModelItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            text = "PrivteLLM",
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            color = AccentBlue.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "ON-DEVICE AI",
                                color = AccentBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.SERVER,
                    onClick = { selectedTab = MainTab.SERVER },
                    icon = { Icon(Icons.Default.Dns, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentGreen,
                        selectedTextColor = AccentGreen,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = AccentGreen.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.MODELS,
                    onClick = { selectedTab = MainTab.MODELS },
                    icon = { Icon(Icons.Default.Store, contentDescription = "Model Store") },
                    label = { Text("Model Store", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = AccentBlue.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.CHAT,
                    onClick = { selectedTab = MainTab.CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Playground") },
                    label = { Text("Playground", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentPurple,
                        selectedTextColor = AccentPurple,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = AccentPurple.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBackground)
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
                        onNavigateToModels = { selectedTab = MainTab.MODELS }
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
                                    withContext(Dispatchers.IO) {
                                        // 4 threads default, 0 GPU layers (CPU NEON) or Vulkan if available, mmap = true, streaming = true
                                        val success = NativeLib.initModel(
                                            path = targetFile.absolutePath,
                                            nThreads = 4,
                                            nGpuLayers = 0,
                                            mmap = true,
                                            streamingExecution = true
                                        )
                                        if (success) {
                                            loadedModel = modelToLoad
                                            LocalAiServerService.setActiveModel(modelToLoad)
                                        }
                                    }
                                }
                            }
                        },
                        onUnloadModel = {
                            coroutineScope.launch(Dispatchers.IO) {
                                NativeLib.releaseModel()
                                loadedModel = null
                                LocalAiServerService.setActiveModel(null)
                            }
                        }
                    )
                }

                MainTab.CHAT -> {
                    ChatPlaygroundScreen(
                        loadedModel = loadedModel
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    memorySnapshot: MemorySnapshot,
    serverState: com.privtellm.server.ServerState,
    loadedModel: ModelItem?,
    onToggleServer: (Boolean) -> Unit,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hardware RAM Meter
        RamMeter(memorySnapshot = memorySnapshot)

        // 2. OpenAI Local Server Card
        ServerStatusCard(
            serverState = serverState,
            onToggleServer = onToggleServer
        )

        // 3. Active Loaded Model Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(BorderColor)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE INFERENCE ENGINE",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (loadedModel != null) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            color = AccentGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "READY",
                                color = AccentGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (loadedModel != null) {
                    Text(
                        text = loadedModel.name,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${loadedModel.parametersBillion}B parameters • Quant: ${loadedModel.quantization} • Context: ${loadedModel.contextLength}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "No model loaded into NDK RAM",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToModels,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Browse Model Store", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
