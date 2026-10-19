package com.privtellm.hub

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    DOWNLOADED,
    LOADED,
    ERROR
}

enum class ModelCapability {
    TEXT_LLM,
    VISION_MULTIMODAL,
    TEXT_TO_SPEECH,
    IMAGE_GENERATION
}

data class ModelItem(
    val id: String,
    val name: String,
    val description: String,
    val parametersBillion: Float,
    val quantization: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String? = null,
    val contextLength: Int = 4096,
    val capability: ModelCapability = ModelCapability.TEXT_LLM,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val downloadSpeedMb: Double = 0.0,
    val localFilePath: String? = null,
    val isRecommended: Boolean = false,
    val isCustom: Boolean = false,
    val recommendationReason: String = "",
    val lastError: String? = null,
    val isLoading: Boolean = false
) {
    val sizeFormatted: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.0f MB", mb)
            }
        }
}

object ModelCatalog {
    private const val TAG = "ModelCatalog"

    fun getInternalModelDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Imports a user-selected .gguf model from Android Storage Access Framework (SAF) URI
     */
    suspend fun importCustomGguf(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<ModelItem> = withContext(Dispatchers.IO) {
        try {
            var fileName = "custom_model_${System.currentTimeMillis()}.gguf"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (!fileName.endsWith(".gguf", ignoreCase = true)) {
                fileName += ".gguf"
            }

            // Clean file name
            val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetDir = getInternalModelDir(context)
            val targetFile = File(targetDir, safeFileName)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open input stream for $uri"))

            var totalCopied = 0L
            val buffer = ByteArray(64 * 1024)

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalCopied += bytesRead
                        if (fileSize > 0) {
                            onProgress(totalCopied.toFloat() / fileSize)
                        }
                    }
                    output.flush()
                }
            }

            val finalSize = if (targetFile.length() > 0) targetFile.length() else totalCopied
            val cleanId = safeFileName.removeSuffix(".gguf").lowercase()

            val customModel = ModelItem(
                id = cleanId,
                name = safeFileName.removeSuffix(".gguf").replace("_", " ").replace("-", " ").capitalizeWords(),
                description = "Custom imported GGUF model from device storage (${(finalSize / (1024 * 1024))} MB).",
                parametersBillion = 1.0f,
                quantization = "Custom",
                sizeBytes = finalSize,
                downloadUrl = "",
                status = DownloadStatus.DOWNLOADED,
                localFilePath = targetFile.absolutePath,
                isCustom = true
            )

            Log.i(TAG, "Successfully imported custom GGUF model: ${targetFile.absolutePath}")
            Result.success(customModel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import custom GGUF: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /**
     * Curated catalog of tested models including Vision, LLM, TTS, and Image Gen
     */
    val CURATED_MODELS = listOf(
        // --- 1. Compact Edge Text LLMs ---
        ModelItem(
            id = "smollm-360m-instruct-q4_k_m",
            name = "SmolLM 360M Instruct",
            description = "Ultra-compact model tailored for resource-constrained edge devices with instant response time.",
            parametersBillion = 0.36f,
            quantization = "Q4_K_M",
            sizeBytes = 270590400L,
            downloadUrl = "https://huggingface.co/QuantFactory/SmolLM-360M-Instruct-GGUF/resolve/main/SmolLM-360M-Instruct.Q4_K_M.gguf",
            contextLength = 2048,
            capability = ModelCapability.TEXT_LLM
        ),
        ModelItem(
            id = "qwen-2.5-0.5b-instruct-q4_k_m",
            name = "Qwen 2.5 0.5B Instruct",
            description = "Strong multilingual and coding foundation in half a billion parameters.",
            parametersBillion = 0.5f,
            quantization = "Q4_K_M",
            sizeBytes = 395L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sha256 = "f0525d88698c9f0ef94473ce183a699df8a34d8e578fa6cb030f2824888be6aa",
            contextLength = 4096,
            capability = ModelCapability.TEXT_LLM
        ),
        ModelItem(
            id = "llama-3.2-1b-instruct-q4_k_m",
            name = "Llama 3.2 1B Instruct",
            description = "State-of-the-art lightweight model from Meta fine-tuned for conversational instruction following.",
            parametersBillion = 1.2f,
            quantization = "Q4_K_M",
            sizeBytes = 808L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sha256 = "d572db0dfd67bb017a421b33fb633db523c9071c778fa9c6691c28c869911e02",
            contextLength = 4096,
            capability = ModelCapability.TEXT_LLM
        ),

        // --- 2. Multimodal / Vision Models (Image Readers) ---
        ModelItem(
            id = "smolvlm-500m-instruct-q4_k_m",
            name = "SmolVLM 500M Vision",
            description = "Multimodal Vision-Language model capable of reading images, charts, handwriting, and photos on mobile.",
            parametersBillion = 0.55f,
            quantization = "Q4_K_M",
            sizeBytes = 460L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolVLM-Instruct-GGUF/resolve/main/smolvlm-instruct-q4_k_m.gguf",
            contextLength = 4096,
            capability = ModelCapability.VISION_MULTIMODAL
        ),
        ModelItem(
            id = "qwen2-vl-2b-instruct-q4_k_m",
            name = "Qwen2-VL 2B Vision",
            description = "High-accuracy vision reasoning model for document extraction, visual QA, and scene interpretation.",
            parametersBillion = 2.2f,
            quantization = "Q4_K_M",
            sizeBytes = 1420L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/qwen2-vl-2b-instruct-q4_k_m.gguf",
            contextLength = 4096,
            capability = ModelCapability.VISION_MULTIMODAL
        ),

        // --- 3. Text-to-Speech (TTS) Voice Models ---
        ModelItem(
            id = "piper-tts-en-high-voice",
            name = "Piper TTS Neural Voice",
            description = "Fast neural on-device Text-to-Speech synthesis with natural cadence and low RAM footprint.",
            parametersBillion = 0.03f,
            quantization = "FP16",
            sizeBytes = 65L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/high/en_US-lessac-high.onnx",
            contextLength = 1024,
            capability = ModelCapability.TEXT_TO_SPEECH
        ),

        // --- 4. Image Generation Models ---
        ModelItem(
            id = "sd-1.5-fast-diffusion-q4",
            name = "Stable Diffusion 1.5 Edge",
            description = "On-device text-to-image synthesis quantized for modern mobile neural accelerators.",
            parametersBillion = 1.0f,
            quantization = "Q4_0",
            sizeBytes = 1850L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/CompVis/stable-diffusion-v1-5-GGUF/resolve/main/sd-v1-5-q4_0.gguf",
            contextLength = 77,
            capability = ModelCapability.IMAGE_GENERATION
        ),

        // --- 5. High Capability Reasoning LLMs ---
        ModelItem(
            id = "gemma-2-2b-it-q4_k_m",
            name = "Gemma 2 2B Instruct",
            description = "Google DeepMind's compact model with sliding window attention for crisp on-device chat.",
            parametersBillion = 2.6f,
            quantization = "Q4_K_M",
            sizeBytes = 1650L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            contextLength = 4096,
            capability = ModelCapability.TEXT_LLM
        ),
        ModelItem(
            id = "llama-3.2-3b-instruct-q4_k_m",
            name = "Llama 3.2 3B Instruct",
            description = "Sweet spot for mobile performance and complex reasoning with dynamic layer streaming.",
            parametersBillion = 3.2f,
            quantization = "Q4_K_M",
            sizeBytes = 2020L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            contextLength = 4096,
            capability = ModelCapability.TEXT_LLM
        ),
        ModelItem(
            id = "mistral-7b-instruct-v0.3-q4_k_m",
            name = "Mistral 7B Instruct v0.3",
            description = "Industry standard 7B model with function calling and extended 8k context window.",
            parametersBillion = 7.2f,
            quantization = "Q4_K_M",
            sizeBytes = 4370L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
            contextLength = 8192,
            capability = ModelCapability.TEXT_LLM
        ),
        ModelItem(
            id = "llama-3-8b-instruct-q4_k_m",
            name = "Llama 3 8B Instruct",
            description = "Top-tier 8B parameter capability with high nuance and structured JSON generation.",
            parametersBillion = 8.0f,
            quantization = "Q4_K_M",
            sizeBytes = 4920L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
            contextLength = 8192,
            capability = ModelCapability.TEXT_LLM
        ),
        ModelItem(
            id = "qwen2.5-14b-instruct-q4_k_m",
            name = "Qwen 2.5 14B Instruct",
            description = "Heavyweight 14B quantized model for high-RAM mobile workstations and flagship flagships.",
            parametersBillion = 14.7f,
            quantization = "Q4_K_M",
            sizeBytes = 8988110976L,
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-14B-Instruct-GGUF/resolve/main/Qwen2.5-14B-Instruct-Q4_K_M.gguf",
            contextLength = 8192,
            capability = ModelCapability.TEXT_LLM
        ),
        ModelItem(
            id = "mixtral-8x7b-instruct-q3_k_m",
            name = "Mixtral 8x7B MoE Instruct",
            description = "Sparse Mixture-of-Experts activating ~19B parameters for advanced multi-domain mastery.",
            parametersBillion = 19.5f,
            quantization = "Q3_K_M",
            sizeBytes = 13500L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/TheBloke/Mixtral-8x7B-Instruct-v0.1-GGUF/resolve/main/mixtral-8x7b-instruct-v0.1.Q3_K_M.gguf",
            contextLength = 8192,
            capability = ModelCapability.TEXT_LLM
        )
    )
}
