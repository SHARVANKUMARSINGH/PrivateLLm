package com.privtellm.nativebridge

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

interface InferenceCallback {
    fun onToken(token: String)
    fun onComplete(fullText: String, promptTokens: Int, completionTokens: Int, tokensPerSecond: Double)
    fun onError(errorCode: Int, errorMessage: String)
}

data class GenerationChunk(
    val token: String,
    val isFinished: Boolean = false,
    val fullText: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val error: String? = null
)

data class NativeMemoryStats(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val processRssMb: Long,
    val processVmSizeMb: Long
)

object NativeLib {
    private const val TAG = "NativeLib"
    private val isNativeLoaded = AtomicBoolean(false)

    init {
        try {
            System.loadLibrary("privtellm_native")
            isNativeLoaded.set(true)
            Log.i(TAG, "Successfully loaded libprivtellm_native.so")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libprivtellm_native.so: ${e.message}")
        }
    }

    // JNI Declarations
    external fun initModel(
        path: String,
        nThreads: Int,
        nGpuLayers: Int,
        mmap: Boolean,
        streamingExecution: Boolean
    ): Boolean

    external fun generateCompletion(
        prompt: String,
        listener: InferenceCallback
    ): Boolean

    external fun releaseModel()

    external fun stopGeneration()

    external fun isModelLoaded(): Boolean

    external fun getNativeMemoryStats(): LongArray

    fun getMemoryStats(): NativeMemoryStats {
        if (!isNativeLoaded.get()) {
            return NativeMemoryStats(0, 0, 0, 0)
        }
        val raw = getNativeMemoryStats()
        return if (raw.size >= 4) {
            NativeMemoryStats(raw[0], raw[1], raw[2], raw[3])
        } else {
            NativeMemoryStats(0, 0, 0, 0)
        }
    }

    /**
     * Coroutine-first streaming generator for Jetpack Compose UI and Ktor SSE.
     */
    fun generateStream(
        prompt: String
    ): Flow<GenerationChunk> = callbackFlow {
        val listener = object : InferenceCallback {
            override fun onToken(token: String) {
                trySend(
                    GenerationChunk(
                        token = token,
                        isFinished = false
                    )
                )
            }

            override fun onComplete(
                fullText: String,
                promptTokens: Int,
                completionTokens: Int,
                tokensPerSecond: Double
            ) {
                trySend(
                    GenerationChunk(
                        token = "",
                        isFinished = true,
                        fullText = fullText,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        tokensPerSecond = tokensPerSecond
                    )
                )
                close()
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                trySend(
                    GenerationChunk(
                        token = "",
                        isFinished = true,
                        error = "[$errorCode] $errorMessage"
                    )
                )
                close(RuntimeException("Native inference error $errorCode: $errorMessage"))
            }
        }

        // Run inference synchronously on the calling dispatcher thread
        val started = generateCompletion(prompt, listener)
        if (!started) {
            trySend(
                GenerationChunk(
                    token = "",
                    isFinished = true,
                    error = "Failed to launch native inference. Check memory and model state."
                )
            )
            close()
        }

        awaitClose {
            // If the coroutine is cancelled (e.g. client disconnects SSE stream or UI changes)
            stopGeneration()
        }
    }
}
