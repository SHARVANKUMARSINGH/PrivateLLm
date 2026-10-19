package com.privtellm.core.profiler

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.FileReader

enum class MemoryTier {
    TIER_SUB_4GB,     // < 4 GB RAM: <= 1B models (Qwen-0.5B, SmolLM-360M)
    TIER_4GB_TO_6GB,  // 4 GB - 6 GB RAM: 1B - 3B models (Llama-3.2-1B/3B, Gemma-2-2B) with hybrid streaming
    TIER_8GB_TO_12GB, // 8 GB - 12 GB RAM: 7B - 8B models (4-bit Q4_K_M)
    TIER_16GB_PLUS    // >= 16 GB RAM: up to 14B - 20B heavily quantized models
}

enum class MemoryPressure {
    NORMAL,
    MODERATE,
    CRITICAL // Free RAM < 450 MB
}

data class MemorySnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val thresholdBytes: Long,
    val isLowMemory: Boolean,
    val tier: MemoryTier,
    val pressure: MemoryPressure
) {
    val totalRamMb: Long get() = totalRamBytes / (1024 * 1024)
    val availableRamMb: Long get() = availableRamBytes / (1024 * 1024)
    val usedRamMb: Long get() = totalRamMb - availableRamMb
    val usedPercentage: Float get() = if (totalRamMb > 0) (usedRamMb.toFloat() / totalRamMb.toFloat()) else 0f
    val totalRamGb: Float get() = totalRamBytes / (1024f * 1024f * 1024f)
    val availableRamGb: Float get() = availableRamBytes / (1024f * 1024f * 1024f)
}

class DeviceProfiler(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Reads current memory metrics directly from ActivityManager.MemoryInfo
     */
    fun getMemorySnapshot(): MemorySnapshot {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val total = if (memoryInfo.totalMem > 0) memoryInfo.totalMem else readProcMemTotal()
        val avail = memoryInfo.availMem
        val threshold = memoryInfo.threshold
        val isLow = memoryInfo.lowMemory || (avail < 450L * 1024 * 1024)

        val totalGb = total / (1024.0 * 1024.0 * 1024.0)

        // Memory tier categorization matching requirements
        val tier = when {
            totalGb < 4.0 -> MemoryTier.TIER_SUB_4GB
            totalGb < 7.0 -> MemoryTier.TIER_4GB_TO_6GB
            totalGb < 14.0 -> MemoryTier.TIER_8GB_TO_12GB
            else -> MemoryTier.TIER_16GB_PLUS
        }

        val availMb = avail / (1024 * 1024)
        val pressure = when {
            availMb < 450 -> MemoryPressure.CRITICAL
            availMb < 800 || isLow -> MemoryPressure.MODERATE
            else -> MemoryPressure.NORMAL
        }

        return MemorySnapshot(
            totalRamBytes = total,
            availableRamBytes = avail,
            thresholdBytes = threshold,
            isLowMemory = isLow,
            tier = tier,
            pressure = pressure
        )
    }

    /**
     * Periodically queries device memory for reactive UI updates
     */
    fun monitorMemory(intervalMs: Long = 1000): Flow<MemorySnapshot> = flow {
        while (true) {
            emit(getMemorySnapshot())
            delay(intervalMs)
        }
    }

    /**
     * Evaluates if a given model parameter size is recommended for this device
     */
    fun isModelRecommended(paramsBillion: Float): Boolean {
        val tier = getMemorySnapshot().tier
        return when (tier) {
            MemoryTier.TIER_SUB_4GB -> paramsBillion <= 1.0f
            MemoryTier.TIER_4GB_TO_6GB -> paramsBillion in 1.0f..3.5f
            MemoryTier.TIER_8GB_TO_12GB -> paramsBillion in 6.0f..8.5f
            MemoryTier.TIER_16GB_PLUS -> paramsBillion in 13.0f..22.0f
        }
    }

    /**
     * Generates human-readable recommendation explanation
     */
    fun getRecommendationDetails(paramsBillion: Float): String {
        val snapshot = getMemorySnapshot()
        val tier = snapshot.tier
        return when {
            isModelRecommended(paramsBillion) -> {
                when (tier) {
                    MemoryTier.TIER_SUB_4GB ->
                        "Perfect fit for devices with <4GB RAM (Low memory footprint)."
                    MemoryTier.TIER_4GB_TO_6GB ->
                        "Optimal for 4-6GB RAM with hybrid mmap streaming enabled."
                    MemoryTier.TIER_8GB_TO_12GB ->
                        "Full speed 4-bit execution with fast KV cache retention."
                    MemoryTier.TIER_16GB_PLUS ->
                        "High capability reasoning enabled on flagship 16GB+ RAM."
                }
            }
            paramsBillion > getMaxSafeModelSize(tier) -> {
                "Exceeds recommended capacity for your ${String.format("%.1f", snapshot.totalRamGb)} GB RAM. Risk of high memory pressure."
            }
            else -> {
                "Runs comfortably, but higher parameter models are supported by your hardware."
            }
        }
    }

    private fun getMaxSafeModelSize(tier: MemoryTier): Float {
        return when (tier) {
            MemoryTier.TIER_SUB_4GB -> 1.0f
            MemoryTier.TIER_4GB_TO_6GB -> 3.5f
            MemoryTier.TIER_8GB_TO_12GB -> 8.5f
            MemoryTier.TIER_16GB_PLUS -> 22.0f
        }
    }

    private fun readProcMemTotal(): Long {
        return try {
            BufferedReader(FileReader("/proc/meminfo")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("MemTotal:") == true) {
                        val parts = line!!.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            return parts[1].toLong() * 1024L
                        }
                    }
                }
                4L * 1024 * 1024 * 1024 // Fallback 4GB
            }
        } catch (e: Exception) {
            4L * 1024 * 1024 * 1024
        }
    }
}
