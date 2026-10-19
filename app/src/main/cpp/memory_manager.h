#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>
#include "include/llama.h"

#define TAG_PRIVTE_MEM "PrivteLLM_Mem"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG_PRIVTE_MEM, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG_PRIVTE_MEM, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG_PRIVTE_MEM, __VA_ARGS__)

namespace privtellm {

struct MemoryStats {
    uint64_t totalRamMb = 0;
    uint64_t availableRamMb = 0;
    uint64_t processRssMb = 0;
    uint64_t processVmSizeMb = 0;
    bool isCritical = false;
};

class MemoryManager {
public:
    static MemoryManager& getInstance();

    // Query system & process memory metrics
    MemoryStats queryMemoryStats();
    uint64_t getAvailableRamMb();
    uint64_t getProcessRssMb();

    // Safety checks
    bool isMemoryUnderThreshold(uint64_t thresholdMb = 450);

    // Layer streaming & mmap advisory
    void registerMmapRegion(void* address, size_t size, int numLayers);
    void unregisterMmapRegion();
    void adviseLayerActive(int layerIndex);
    void adviseLayerDone(int layerIndex);

    // KV Cache dynamic pruning
    bool maybePruneKvCache(
        struct llama_context* ctx,
        int& currentPos,
        int keepPromptTokens = 32,
        int targetReductionTokens = 128
    );

private:
    MemoryManager();
    ~MemoryManager();

    std::atomic<bool> m_streamingEnabled{false};
    void* m_mmapBaseAddress = nullptr;
    size_t m_mmapTotalSize = 0;
    int m_numLayers = 0;
    size_t m_bytesPerLayer = 0;
    int m_lastActiveLayer = -1;
};

} // namespace privtellm
