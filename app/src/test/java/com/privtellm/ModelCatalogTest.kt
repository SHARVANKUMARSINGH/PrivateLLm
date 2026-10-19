package com.privtellm

import com.privtellm.hub.DownloadStatus
import com.privtellm.hub.ModelCatalog
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun testCuratedModelsCoverage() {
        val models = ModelCatalog.CURATED_MODELS
        assertTrue("Curated list should contain at least 8 models", models.size >= 8)

        // Verify parameter range 0.36B to ~19.5B
        val minParams = models.minOf { it.parametersBillion }
        val maxParams = models.maxOf { it.parametersBillion }
        assertTrue("Minimum parameter size should be <= 0.5B", minParams <= 0.5f)
        assertTrue("Maximum parameter size should be >= 14B", maxParams >= 14.0f)

        // Check specific key models
        assertTrue(models.any { it.id.contains("smollm-360m") })
        assertTrue(models.any { it.id.contains("qwen-2.5-0.5b") })
        assertTrue(models.any { it.id.contains("llama-3.2-1b") })
        assertTrue(models.any { it.id.contains("gemma-2-2b") })
        assertTrue(models.any { it.id.contains("llama-3.2-3b") })
        assertTrue(models.any { it.id.contains("mistral-7b") })
        assertTrue(models.any { it.id.contains("llama-3-8b") })
        assertTrue(models.any { it.id.contains("qwen2.5-14b") })
    }

    @Test
    fun testModelFormattedSizes() {
        val smol = ModelCatalog.CURATED_MODELS.first { it.id.contains("smollm-360m") }
        assertTrue(smol.sizeFormatted.contains("MB") || smol.sizeFormatted.contains("GB"))

        val llama8b = ModelCatalog.CURATED_MODELS.first { it.id.contains("llama-3-8b") }
        assertTrue(llama8b.sizeFormatted.contains("GB"))
    }

    @Test
    fun testInitialStatusIsDefault() {
        ModelCatalog.CURATED_MODELS.forEach { model ->
            assertEquals(DownloadStatus.NOT_DOWNLOADED, model.status)
            assertEquals(0f, model.downloadProgress, 0.001f)
            assertNull(model.localFilePath)
        }
    }
}
