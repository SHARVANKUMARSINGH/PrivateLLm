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
    MODELS("Model Store"),
    CHAT("Playground")
}

class MainActivity : ComponentActivity() {

    private lateinit var profiler: DeviceProfiler
    private lateinit var downloader: ModelDownloader

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

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
                                color = NeonGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElectricCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "v1.0.0",
                                    color = ElectricCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Memory Header Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SpaceSurfaceVariant,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(SpaceCardBorder, SpaceCardBorder))
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
                                        .background(if (memorySnapshot.availableRamMb < 450) CrimsonRed else NeonGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${memorySnapshot.availableRamMb} MB Free",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpaceBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SpaceSurface,
                contentColor = TextPrimary
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.SERVER,
                    onClick = { selectedTab = MainTab.SERVER },
                    icon = { Icon(Icons.Default.Dns, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonGreen,
                        selectedTextColor = NeonGreen,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonGreenGlow
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.MODELS,
                    onClick = { selectedTab = MainTab.MODELS },
                    icon = { Icon(Icons.Default.Store, contentDescription = "Model Store") },
                    label = { Text("Model Store", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = ElectricCyanGlow
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.CHAT,
                    onClick = { selectedTab = MainTab.CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Playground") },
                    label = { Text("Playground", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleAccent,
                        selectedTextColor = PurpleAccent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = Color(0x338B5CF6)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SpaceBackground)
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
                                NativeLib.releaseModel()
                                loadedModel = null
                                LocalAiServerService.setActiveModel(null)
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
                                    withContext(Dispatchers.IO) {
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
    serverState: ServerState,
    loadedModel: ModelItem?,
    onToggleServer: (Boolean) -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToChat: () -> Unit,
    onUnloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        // 3. Active Loaded Model HUD Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    if (loadedModel != null)
                        listOf(NeonGreen.copy(alpha = 0.6f), SpaceCardBorder)
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
                                .background(if (loadedModel != null) NeonGreenGlow else SpaceSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Active Engine",
                                tint = if (loadedModel != null) NeonGreen else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "ACTIVE INFERENCE ENGINE",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = if (loadedModel != null) "C++20 NDK Tensor Pipeline Active" else "No Weights Loaded in RAM",
                                color = if (loadedModel != null) NeonGreenDim else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (loadedModel != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonGreenGlow
                        ) {
                            Text(
                                text = "PRIME",
                                color = NeonGreen,
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
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${loadedModel.parametersBillion}B params • ${loadedModel.quantization} • ${loadedModel.contextLength} context window • ${loadedModel.sizeFormatted}",
                        color = TextSecondary,
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
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = SpaceBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Chat", color = SpaceBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onUnloadModel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(AmberAccent.copy(alpha = 0.6f), AmberAccent.copy(alpha = 0.2f)))
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Unload RAM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "Native RAM is currently cleared. Download and load a quantized model from the Model Store to start local inference.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToModels,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = SpaceBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse Curated Models", color = SpaceBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Hardware System Specifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(SpaceCardBorder, SpaceCardBorder))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "ACCELERATION & RUNTIME SPECS",
                    color = TextSecondary,
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
    Column {
        Text(title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}
