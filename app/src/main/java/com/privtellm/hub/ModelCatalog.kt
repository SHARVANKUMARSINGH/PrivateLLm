package com.privtellm.hub

import android.content.Context
import java.io.File

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    DOWNLOADED,
    LOADED,
    ERROR
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
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val downloadSpeedMb: Double = 0.0,
    val localFilePath: String? = null,
    val isRecommended: Boolean = false,
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

    fun getInternalModelDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    val CURATED_MODELS = listOf(
        ModelItem(
            id = "smollm-360m-instruct-q4_k_m",
            name = "SmolLM 360M Instruct",
            description = "Ultra-compact model tailored for resource-constrained edge devices with instant response time.",
            parametersBillion = 0.36f,
            quantization = "Q4_K_M",
            sizeBytes = 270590400L,
            downloadUrl = "https://huggingface.co/QuantFactory/SmolLM-360M-Instruct-GGUF/resolve/main/SmolLM-360M-Instruct.Q4_K_M.gguf",
            contextLength = 2048
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
            contextLength = 4096
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
            contextLength = 4096
        ),
        ModelItem(
            id = "gemma-2-2b-it-q4_k_m",
            name = "Gemma 2 2B Instruct",
            description = "Google DeepMind's compact model with sliding window attention for crisp on-device chat.",
            parametersBillion = 2.6f,
            quantization = "Q4_K_M",
            sizeBytes = 1650L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            contextLength = 4096
        ),
        ModelItem(
            id = "llama-3.2-3b-instruct-q4_k_m",
            name = "Llama 3.2 3B Instruct",
            description = "Sweet spot for mobile performance and complex reasoning with dynamic layer streaming.",
            parametersBillion = 3.2f,
            quantization = "Q4_K_M",
            sizeBytes = 2020L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            contextLength = 4096
        ),
        ModelItem(
            id = "mistral-7b-instruct-v0.3-q4_k_m",
            name = "Mistral 7B Instruct v0.3",
            description = "Industry standard 7B model with function calling and extended 8k context window.",
            parametersBillion = 7.2f,
            quantization = "Q4_K_M",
            sizeBytes = 4370L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
            contextLength = 8192
        ),
        ModelItem(
            id = "llama-3-8b-instruct-q4_k_m",
            name = "Llama 3 8B Instruct",
            description = "Top-tier 8B parameter capability with high nuance and structured JSON generation.",
            parametersBillion = 8.0f,
            quantization = "Q4_K_M",
            sizeBytes = 4920L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
            contextLength = 8192
        ),
        ModelItem(
            id = "qwen2.5-14b-instruct-q4_k_m",
            name = "Qwen 2.5 14B Instruct",
            description = "Heavyweight 14B quantized model for high-RAM mobile workstations and flagship flagships.",
            parametersBillion = 14.7f,
            quantization = "Q4_K_M",
            sizeBytes = 8988110976L,
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-14B-Instruct-GGUF/resolve/main/Qwen2.5-14B-Instruct-Q4_K_M.gguf",
            contextLength = 8192
        ),
        ModelItem(
            id = "mixtral-8x7b-instruct-q3_k_m",
            name = "Mixtral 8x7B MoE Instruct",
            description = "Sparse Mixture-of-Experts activating ~19B parameters for advanced multi-domain mastery.",
            parametersBillion = 19.5f,
            quantization = "Q3_K_M",
            sizeBytes = 13500L * 1024 * 1024,
            downloadUrl = "https://huggingface.co/TheBloke/Mixtral-8x7B-Instruct-v0.1-GGUF/resolve/main/mixtral-8x7b-instruct-v0.1.Q3_K_M.gguf",
            contextLength = 8192
        )
    )
}
