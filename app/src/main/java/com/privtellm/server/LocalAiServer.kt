package com.privtellm.server

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.privtellm.PrivteLLMApp
import com.privtellm.core.profiler.DeviceProfiler
import com.privtellm.core.safety.MemoryWatcher
import com.privtellm.hub.DownloadStatus
import com.privtellm.hub.ModelCatalog
import com.privtellm.hub.ModelItem
import com.privtellm.nativebridge.GenerationChunk
import com.privtellm.nativebridge.NativeLib
import com.privtellm.ui.MainActivity
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class ServerState(
    val status: ServerStatus = ServerStatus.STOPPED,
    val port: Int = 8080,
    val localIp: String = "127.0.0.1",
    val activeModel: ModelItem? = null,
    val totalRequests: Long = 0,
    val totalTokens: Long = 0,
    val isInferring: Boolean = false,
    val lastError: String? = null
)

class LocalAiServerService : Service() {

    companion object {
        private const val TAG = "LocalAiServerService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.privtellm.action.START_SERVER"
        const val ACTION_STOP = "com.privtellm.action.STOP_SERVER"

        private val _serverState = MutableStateFlow(ServerState())
        val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, LocalAiServerService::class.java).apply {
                action = ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocalAiServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setActiveModel(model: ModelItem?) {
            _serverState.value = _serverState.value.copy(activeModel = model)
        }
    }

    private var serverEngine: ApplicationEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var memoryWatcher: MemoryWatcher
    private lateinit var profiler: DeviceProfiler

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    override fun onCreate() {
        super.onCreate()
        memoryWatcher = MemoryWatcher(this)
        profiler = DeviceProfiler(this)
        memoryWatcher.start()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrivteLLM:AiServerWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (_serverState.value.status == ServerStatus.RUNNING) return

        _serverState.value = _serverState.value.copy(
            status = ServerStatus.STARTING,
            localIp = NetworkUtils.getLocalIpAddress(this)
        )

        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours wake lock max
        startForeground(NOTIFICATION_ID, createNotification("Starting server..."))

        serviceScope.launch {
            try {
                val port = 8080
                val ip = NetworkUtils.getLocalIpAddress(this@LocalAiServerService)

                serverEngine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                    install(CORS) {
                        anyHost()
                        allowHeader(HttpHeaders.ContentType)
                        allowHeader(HttpHeaders.Authorization)
                        allowMethod(HttpMethod.Options)
                        allowMethod(HttpMethod.Get)
                        allowMethod(HttpMethod.Post)
                    }

                    install(ContentNegotiation) {
                        json(json)
                    }

                    install(StatusPages) {
                        exception<Throwable> { call, cause ->
                            Log.e(TAG, "Unhandled server error: ${cause.message}", cause)
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiErrorResponse(ApiError(cause.message ?: "Internal Server Error", code = 500))
                            )
                        }
                    }

                    routing {
                        // Health / Status endpoint
                        get("/") {
                            val ipAddr = NetworkUtils.getLocalIpAddress(this@LocalAiServerService)
                            val memory = profiler.getMemorySnapshot()
                            call.respondText(
                                """
                                {
                                  "service": "PrivteLLM On-Device Local OpenAI API",
                                  "status": "online",
                                  "ip": "$ipAddr",
                                  "port": $port,
                                  "active_model": "${_serverState.value.activeModel?.name ?: "none"}",
                                  "free_ram_mb": ${memory.availableRamMb},
                                  "is_inferring": ${_serverState.value.isInferring}
                                }
                                """.trimIndent(),
                                ContentType.Application.Json
                            )
                        }

                        // OpenAI: GET /v1/models
                        get("/v1/models") {
                            val modelDir = ModelCatalog.getInternalModelDir(this@LocalAiServerService)
                            val installedFiles = modelDir.listFiles { _, name -> name.endsWith(".gguf") } ?: emptyArray()

                            val modelsData = mutableListOf<ModelData>()
                            installedFiles.forEach { file ->
                                modelsData.add(
                                    ModelData(
                                        id = file.nameWithoutExtension,
                                        created = file.lastModified() / 1000L,
                                        ownedBy = "privtellm-ondevice"
                                    )
                                )
                            }

                            // If an active in-memory model exists, ensure it is listed
                            _serverState.value.activeModel?.let { active ->
                                if (modelsData.none { it.id == active.id }) {
                                    modelsData.add(
                                        ModelData(
                                            id = active.id,
                                            created = System.currentTimeMillis() / 1000L,
                                            ownedBy = "privtellm-loaded"
                                        )
                                    )
                                }
                            }

                            call.respond(ModelListResponse(data = modelsData))
                        }

                        // OpenAI: POST /v1/chat/completions
                        post("/v1/chat/completions") {
                            handleChatCompletions(call)
                        }
                    }
                }

                serverEngine?.start(wait = false)

                _serverState.value = _serverState.value.copy(
                    status = ServerStatus.RUNNING,
                    port = port,
                    localIp = ip,
                    lastError = null
                )

                updateNotification("Running at http://$ip:$port")
                Log.i(TAG, "PrivteLLM OpenAI API Server successfully listening on http://$ip:$port")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Ktor server: ${e.message}", e)
                _serverState.value = _serverState.value.copy(
                    status = ServerStatus.ERROR,
                    lastError = e.message
                )
                updateNotification("Error starting server: ${e.message}")
            }
        }
    }

    private suspend fun handleChatCompletions(call: ApplicationCall) {
        // 1. Safety Guardrail: Check memory threshold (450 MB)
        if (!memoryWatcher.isMemorySafe()) {
            val freeMb = memoryWatcher.getAvailableRamMb()
            val err = "Device memory critical: only ${freeMb} MB free RAM remaining. Request intercepted to prevent system OOM."
            Log.e(TAG, err)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiErrorResponse(ApiError(err, type = "insufficient_device_memory", code = 429))
            )
            return
        }

        // 2. Parse OpenAI Request Body
        val request = try {
            call.receive<ChatCompletionRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(ApiError("Invalid JSON body: ${e.message}", type = "invalid_request_error", code = 400))
            )
            return
        }

        // 3. Format Prompt from Messages (ChatML standard)
        val prompt = formatChatPrompt(request.messages)
        val completionId = "chatcmpl-${UUID.randomUUID().toString().take(12)}"
        val created = System.currentTimeMillis() / 1000L
        val modelName = request.model

        _serverState.value = _serverState.value.copy(
            isInferring = true,
            totalRequests = _serverState.value.totalRequests + 1
        )

        try {
            val isStreaming = request.stream == true

            if (isStreaming) {
                // SSE / Server-Sent Events Streaming Mode
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    val streamFlow = NativeLib.generateStream(prompt)

                    // Initial role chunk
                    val initialChunk = ChatCompletionChunk(
                        id = completionId,
                        created = created,
                        model = modelName,
                        choices = listOf(
                            ChunkChoice(index = 0, delta = ChunkDelta(role = "assistant", content = ""))
                        )
                    )
                    write("data: ${json.encodeToString(initialChunk)}\n\n")
                    flush()

                    var generatedCount = 0L

                    streamFlow.collect { chunk: GenerationChunk ->
                        if (chunk.token.isNotEmpty()) {
                            generatedCount++
                            val payload = ChatCompletionChunk(
                                id = completionId,
                                created = created,
                                model = modelName,
                                choices = listOf(
                                    ChunkChoice(index = 0, delta = ChunkDelta(content = chunk.token))
                                )
                            )
                            write("data: ${json.encodeToString(payload)}\n\n")
                            flush()
                        }

                        if (chunk.isFinished) {
                            val stopPayload = ChatCompletionChunk(
                                id = completionId,
                                created = created,
                                model = modelName,
                                choices = listOf(
                                    ChunkChoice(index = 0, delta = ChunkDelta(), finishReason = "stop")
                                )
                            )
                            write("data: ${json.encodeToString(stopPayload)}\n\n")
                            write("data: [DONE]\n\n")
                            flush()
                        }
                    }

                    _serverState.value = _serverState.value.copy(
                        totalTokens = _serverState.value.totalTokens + generatedCount
                    )
                }
            } else {
                // Standard Non-Streaming JSON Response
                val fullText = StringBuilder()
                var promptTokens = 0
                var completionTokens = 0

                val streamFlow = NativeLib.generateStream(prompt)
                streamFlow.collect { chunk ->
                    if (chunk.token.isNotEmpty()) {
                        fullText.append(chunk.token)
                    }
                    if (chunk.isFinished) {
                        promptTokens = chunk.promptTokens
                        completionTokens = chunk.completionTokens
                    }
                }

                val response = ChatCompletionResponse(
                    id = completionId,
                    created = created,
                    model = modelName,
                    choices = listOf(
                        Choice(
                            index = 0,
                            message = ChatMessage(role = "assistant", content = fullText.toString()),
                            finishReason = "stop"
                        )
                    ),
                    usage = Usage(
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = promptTokens + completionTokens
                    )
                )

                _serverState.value = _serverState.value.copy(
                    totalTokens = _serverState.value.totalTokens + completionTokens
                )

                call.respond(HttpStatusCode.OK, response)
            }
        } finally {
            _serverState.value = _serverState.value.copy(isInferring = false)
        }
    }

    private fun formatChatPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            when (msg.role.lowercase()) {
                "system" -> sb.append("<|im_start|>system\n").append(msg.content.trim()).append("<|im_end|>\n")
                "user" -> sb.append("<|im_start|>user\n").append(msg.content.trim()).append("<|im_end|>\n")
                "assistant" -> sb.append("<|im_start|>assistant\n").append(msg.content.trim()).append("<|im_end|>\n")
                else -> sb.append("<|im_start|>user\n").append(msg.content.trim()).append("<|im_end|>\n")
            }
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun stopServer() {
        serviceScope.launch {
            try {
                NativeLib.stopGeneration()
                serverEngine?.stop(1000, 2000)
                serverEngine = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server engine: ${e.message}")
            } finally {
                _serverState.value = _serverState.value.copy(
                    status = ServerStatus.STOPPED,
                    isInferring = false
                )
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PrivteLLMApp.CHANNEL_ID)
            .setContentTitle("PrivteLLM OpenAI API Server")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val notification = createNotification(statusText)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        memoryWatcher.stop()
        stopServer()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
