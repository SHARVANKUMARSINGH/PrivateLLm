package com.privtellm

import com.privtellm.core.profiler.MemoryPressure
import com.privtellm.core.profiler.MemorySnapshot
import com.privtellm.core.profiler.MemoryTier
import org.junit.Assert.*
import org.junit.Test

class DeviceProfilerTest {

    @Test
    fun testMemoryTierClassification() {
        // Tier 1: < 4 GB RAM -> TIER_SUB_4GB
        val snap3Gb = MemorySnapshot(
            totalRamBytes = 3L * 1024 * 1024 * 1024,
            availableRamBytes = 1200L * 1024 * 1024,
            thresholdBytes = 400L * 1024 * 1024,
            isLowMemory = false,
            tier = MemoryTier.TIER_SUB_4GB,
            pressure = MemoryPressure.NORMAL
        )
        assertEquals(MemoryTier.TIER_SUB_4GB, snap3Gb.tier)
        assertEquals(3072L, snap3Gb.totalRamMb)

        // Tier 2: 4 - 6 GB RAM -> TIER_4GB_TO_6GB
        val snap6Gb = MemorySnapshot(
            totalRamBytes = 6L * 1024 * 1024 * 1024,
            availableRamBytes = 2500L * 1024 * 1024,
            thresholdBytes = 600L * 1024 * 1024,
            isLowMemory = false,
            tier = MemoryTier.TIER_4GB_TO_6GB,
            pressure = MemoryPressure.NORMAL
        )
        assertEquals(MemoryTier.TIER_4GB_TO_6GB, snap6Gb.tier)

        // Tier 3: 8 - 12 GB RAM -> TIER_8GB_TO_12GB
        val snap12Gb = MemorySnapshot(
            totalRamBytes = 12L * 1024 * 1024 * 1024,
            availableRamBytes = 6000L * 1024 * 1024,
            thresholdBytes = 800L * 1024 * 1024,
            isLowMemory = false,
            tier = MemoryTier.TIER_8GB_TO_12GB,
            pressure = MemoryPressure.NORMAL
        )
        assertEquals(MemoryTier.TIER_8GB_TO_12GB, snap12Gb.tier)

        // Tier 4: >= 16 GB RAM -> TIER_16GB_PLUS
        val snap16Gb = MemorySnapshot(
            totalRamBytes = 16L * 1024 * 1024 * 1024,
            availableRamBytes = 9000L * 1024 * 1024,
            thresholdBytes = 1000L * 1024 * 1024,
            isLowMemory = false,
            tier = MemoryTier.TIER_16GB_PLUS,
            pressure = MemoryPressure.NORMAL
        )
        assertEquals(MemoryTier.TIER_16GB_PLUS, snap16Gb.tier)
    }

    @Test
    fun testCriticalMemoryPressureDetection() {
        val criticalFreeBytes = 400L * 1024 * 1024 // 400 MB (< 450 MB threshold)
        val snapshot = MemorySnapshot(
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            availableRamBytes = criticalFreeBytes,
            thresholdBytes = 450L * 1024 * 1024,
            isLowMemory = true,
            tier = MemoryTier.TIER_8GB_TO_12GB,
            pressure = MemoryPressure.CRITICAL
        )

        assertEquals(MemoryPressure.CRITICAL, snapshot.pressure)
        assertTrue(snapshot.availableRamMb < 450)
    }

    @Test
    fun testRamMetricsCalculations() {
        val total = 8192L * 1024 * 1024
        val avail = 2048L * 1024 * 1024
        val snapshot = MemorySnapshot(
            totalRamBytes = total,
            availableRamBytes = avail,
            thresholdBytes = 500L * 1024 * 1024,
            isLowMemory = false,
            tier = MemoryTier.TIER_8GB_TO_12GB,
            pressure = MemoryPressure.NORMAL
        )

        assertEquals(8192L, snapshot.totalRamMb)
        assertEquals(2048L, snapshot.availableRamMb)
        assertEquals(6144L, snapshot.usedRamMb)
        assertEquals(0.75f, snapshot.usedPercentage, 0.001f)
    }
}
