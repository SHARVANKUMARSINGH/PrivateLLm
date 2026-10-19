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
    val lastError: String? = null,
    val apiKey: String = "sk-privtellm-live-key",
    val requireApiKey: Boolean = true
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
            try {
                val intent = Intent(context, LocalAiServerService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "startService ACTION_STOP failed: ${e.message}, falling back to stopService", e)
                try {
                    context.stopService(Intent(context, LocalAiServerService::class.java))
                } catch (_: Exception) {}
            }
        }

        fun setActiveModel(model: ModelItem?) {
            _serverState.value = _serverState.value.copy(activeModel = model)
        }

        fun setApiKey(apiKey: String) {
            _serverState.value = _serverState.value.copy(apiKey = apiKey)
        }

        fun setRequireApiKey(required: Boolean) {
            _serverState.value = _serverState.value.copy(requireApiKey = required)
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
        isStopping = false

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
                        // Web UI or JSON status based on Accept header
                        get("/") {
                            val accept = call.request.headers[HttpHeaders.Accept] ?: ""
                            if (accept.contains("text/html") || !accept.contains("application/json")) {
                                call.respondText(loadWebHtml(), ContentType.Text.Html)
                            } else {
                                respondStatusJson(call, port)
                            }
                        }

                        get("/chat") {
                            call.respondText(loadWebHtml(), ContentType.Text.Html)
                        }

                        get("/web") {
                            call.respondText(loadWebHtml(), ContentType.Text.Html)
                        }

                        get("/v1/status") {
                            respondStatusJson(call, port)
                        }

                        // OpenAI: GET /v1/models
                        get("/v1/models") {
                            if (!validateApiKey(call)) return@get

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
                            if (!validateApiKey(call)) return@post
                            handleChatCompletions(call)
                        }

                        // OpenAI Extension: POST /v1/models/load or /v1/models/select
                        post("/v1/models/load") {
                            if (!validateApiKey(call)) return@post
                            val req = try { call.receive<ModelSelectRequest>() } catch (_: Exception) { null }
                            if (req == null) {
                                call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("Invalid body: expected {\"model\": \"...\"}", code = 400)))
                                return@post
                            }
                            val switched = switchModelById(req.model)
                            if (switched) {
                                call.respond(ModelSelectResponse(activeModel = req.model))
                            } else {
                                call.respond(HttpStatusCode.NotFound, ApiErrorResponse(ApiError("Model '${req.model}' not found in installed storage or failed to load.", code = 404)))
                            }
                        }

                        post("/v1/models/select") {
                            if (!validateApiKey(call)) return@post
                            val req = try { call.receive<ModelSelectRequest>() } catch (_: Exception) { null }
                            if (req == null) {
                                call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("Invalid body: expected {\"model\": \"...\"}", code = 400)))
                                return@post
                            }
                            val switched = switchModelById(req.model)
                            if (switched) {
                                call.respond(ModelSelectResponse(activeModel = req.model))
                            } else {
                                call.respond(HttpStatusCode.NotFound, ApiErrorResponse(ApiError("Model '${req.model}' not found in installed storage or failed to load.", code = 404)))
                            }
                        }

                        // OpenAI: POST /v1/audio/speech (TTS synthesis endpoint)
                        post("/v1/audio/speech") {
                            if (!validateApiKey(call)) return@post
                            val req = try { call.receive<SpeechRequest>() } catch (_: Exception) { null }
                            if (req == null) {
                                call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("Invalid speech request body", code = 400)))
                                return@post
                            }
                            call.respond(SpeechResponse(charactersCount = req.input.length))
                        }

                        // OpenAI: POST /v1/images/generations (Image generation endpoint)
                        post("/v1/images/generations") {
                            if (!validateApiKey(call)) return@post
                            val req = try { call.receive<ImageGenerationRequest>() } catch (_: Exception) { null }
                            if (req == null) {
                                call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("Invalid image generation request body", code = 400)))
                                return@post
                            }
                            val encodedPrompt = req.prompt.take(40).replace("<", "").replace(">", "")
                            val dummySvg = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='512' height='512' style='background:%230A0E17'><text x='50%25' y='50%25' fill='%2300D2FF' font-family='sans-serif' font-size='20' text-anchor='middle'>PrivteLLM Image Engine: $encodedPrompt</text></svg>"
                            call.respond(ImageGenerationResponse(data = listOf(ImageItem(url = dummySvg))))
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

    private fun loadWebHtml(): String {
        return try {
            assets.open("web/index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            """<!DOCTYPE html><html><head><meta charset="utf-8"><title>PrivteLLM</title></head><body style="background:#0a0e17;color:#fff;font-family:sans-serif;padding:30px;"><h2>PrivteLLM Web Client</h2><p>Please open the PrivteLLM Android app to load a model.</p></body></html>"""
        }
    }

    private suspend fun validateApiKey(call: ApplicationCall): Boolean {
        val state = _serverState.value
        if (!state.requireApiKey) return true

        val authHeader = call.request.headers[HttpHeaders.Authorization]
        val customKeyHeader = call.request.headers["api-key"] ?: call.request.headers["x-api-key"]

        val token = when {
            authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true) ->
                authHeader.substring(7).trim()
            customKeyHeader != null ->
                customKeyHeader.trim()
            else -> null
        }

        if (token == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiErrorResponse(ApiError("Missing or invalid API key. Header must be: 'Authorization: Bearer <key>' or 'api-key: <key>'.", type = "invalid_api_key", code = 401))
            )
            return false
        }

        if (token != state.apiKey) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiErrorResponse(ApiError("Incorrect API key provided.", type = "invalid_api_key", code = 401))
            )
            return false
        }
        return true
    }

    private suspend fun switchModelById(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val modelDir = ModelCatalog.getInternalModelDir(this@LocalAiServerService)
        val candidate = File(modelDir, "$modelId.gguf")
        val altCandidate = modelDir.listFiles { _, name ->
            name.equals("$modelId.gguf", ignoreCase = true) ||
            name.removeSuffix(".gguf").equals(modelId, ignoreCase = true)
        }?.firstOrNull() ?: candidate

        if (!altCandidate.exists()) {
            Log.w(TAG, "Cannot switch to model '$modelId': file not found at ${altCandidate.absolutePath}")
            return@withContext false
        }

        try {
            NativeLib.releaseModelSafe()
            val initSuccess = NativeLib.initModelSafe(
                path = altCandidate.absolutePath,
                nThreads = 4,
                nGpuLayers = 0,
                mmap = true,
                streamingExecution = true
            )
            if (initSuccess) {
                val matchingCurated = ModelCatalog.CURATED_MODELS.find { it.id == modelId }
                val newModel = matchingCurated ?: ModelItem(
                    id = modelId,
                    name = altCandidate.nameWithoutExtension,
                    description = "Active model",
                    parametersBillion = 1.0f,
                    quantization = "GGUF",
                    sizeBytes = altCandidate.length(),
                    downloadUrl = "",
                    status = DownloadStatus.LOADED,
                    localFilePath = altCandidate.absolutePath
                )
                _serverState.value = _serverState.value.copy(activeModel = newModel)
                Log.i(TAG, "Server successfully switched active model to: ${newModel.name}")
                return@withContext true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to switch model to $modelId: ${e.message}", e)
        }
        return@withContext false
    }


    private suspend fun respondStatusJson(call: ApplicationCall, port: Int) {
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
              "is_inferring": ${_serverState.value.isInferring},
              "requires_api_key": ${_serverState.value.requireApiKey}
            }
            """.trimIndent(),
            ContentType.Application.Json
        )
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

        // 2b. Dynamic Model Switching: If request specifies an installed model different from active, switch dynamically
        if (request.model.isNotBlank() && request.model != "privtellm-default" && request.model != _serverState.value.activeModel?.id) {
            val modelDir = ModelCatalog.getInternalModelDir(this@LocalAiServerService)
            val hasFile = modelDir.listFiles { _, name ->
                name.equals("${request.model}.gguf", ignoreCase = true) ||
                name.removeSuffix(".gguf").equals(request.model, ignoreCase = true)
            }?.isNotEmpty() == true
            if (hasFile) {
                switchModelById(request.model)
            }
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

    @Volatile
    private var isStopping = false

    private fun stopServer() {
        if (isStopping) return
        isStopping = true

        _serverState.value = _serverState.value.copy(
            status = ServerStatus.STOPPED,
            isInferring = false
        )

        // Stop active native generation safely
        try {
            NativeLib.stopGeneration()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping native generation: ${e.message}")
        }

        // Release wake lock safely
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }

        // Remove foreground notification safely
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                stopForeground(true)
            } catch (_: Exception) {}
        }

        // Stop Ktor server engine on an independent background IO scope
        val engineToStop = serverEngine
        serverEngine = null
        if (engineToStop != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    engineToStop.stop(500, 1000)
                    Log.i(TAG, "Ktor server engine stopped successfully.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error shutting down Ktor engine: ${e.message}")
                }
            }
        }

        stopSelf()
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
        try {
            memoryWatcher.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping memory watcher: ${e.message}")
        }
        if (!isStopping) {
            stopServer()
        }
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling serviceScope: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
