package com.privtellm.core.safety

import android.content.Context
import android.util.Log
import com.privtellm.core.profiler.DeviceProfiler
import com.privtellm.nativebridge.NativeLib
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class MemoryWatcher(private val context: Context) {

    companion object {
        private const val TAG = "MemoryWatcher"
        const val CRITICAL_THRESHOLD_MB = 450L
        const val WARNING_THRESHOLD_MB = 650L
    }

    private val profiler = DeviceProfiler(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var watcherJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    private val _criticalEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val criticalEvents: SharedFlow<Long> = _criticalEvents.asSharedFlow()

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting active memory watcher (threshold: ${CRITICAL_THRESHOLD_MB}MB)")
            watcherJob = scope.launch {
                while (isActive) {
                    val snapshot = profiler.getMemorySnapshot()
                    val freeMb = snapshot.availableRamMb

                    if (freeMb < CRITICAL_THRESHOLD_MB) {
                        Log.e(TAG, "CRITICAL RAM LEVEL: Only ${freeMb}MB remaining! Terminating native inference.")
                        // Intercept & abort active native generation cleanly
                        NativeLib.stopGeneration()
                        _criticalEvents.tryEmit(freeMb)
                    } else if (freeMb < WARNING_THRESHOLD_MB) {
                        Log.w(TAG, "Memory warning: Free RAM is low (${freeMb}MB).")
                    }

                    delay(400)
                }
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            watcherJob?.cancel()
            watcherJob = null
            Log.i(TAG, "Memory watcher stopped.")
        }
    }

    /**
     * Pre-flight guardrail called before starting inference or admitting API requests.
     */
    fun isMemorySafe(): Boolean {
        val freeMb = profiler.getMemorySnapshot().availableRamMb
        return freeMb >= CRITICAL_THRESHOLD_MB
    }

    fun getAvailableRamMb(): Long {
        return profiler.getMemorySnapshot().availableRamMb
    }

    /**
     * Throws an exception or returns error state if memory is insufficient
     */
    fun checkAdmission(): Result<Unit> {
        val freeMb = getAvailableRamMb()
        return if (freeMb >= CRITICAL_THRESHOLD_MB) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "Device memory critical: Only ${freeMb} MB free (minimum ${CRITICAL_THRESHOLD_MB} MB required). Request rejected to prevent system OOM."
                )
            )
        }
    }
}
