#include "memory_manager.h"
#include <fstream>
#include <sstream>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/sysinfo.h>
#include <algorithm>

namespace privtellm {

MemoryManager& MemoryManager::getInstance() {
    static MemoryManager instance;
    return instance;
}

MemoryManager::MemoryManager() = default;

MemoryManager::~MemoryManager() {
    unregisterMmapRegion();
}

MemoryStats MemoryManager::queryMemoryStats() {
    MemoryStats stats;

    // 1. Read system memory from /proc/meminfo
    std::ifstream meminfo("/proc/meminfo");
    if (meminfo.is_open()) {
        std::string line;
        while (std::getline(meminfo, line)) {
            if (line.rfind("MemTotal:", 0) == 0) {
                uint64_t kb = 0;
                std::sscanf(line.c_str(), "MemTotal: %llu kB", &kb);
                stats.totalRamMb = kb / 1024;
            } else if (line.rfind("MemAvailable:", 0) == 0) {
                uint64_t kb = 0;
                std::sscanf(line.c_str(), "MemAvailable: %llu kB", &kb);
                stats.availableRamMb = kb / 1024;
            }
        }
    } else {
        // Fallback to sysinfo syscall
        struct sysinfo si;
        if (sysinfo(&si) == 0) {
            stats.totalRamMb = (si.totalram * si.mem_unit) / (1024 * 1024);
            stats.availableRamMb = (si.freeram * si.mem_unit) / (1024 * 1024);
        }
    }

    // 2. Read process memory from /proc/self/statm
    // Fields: size, resident, shared, text, lib, data, dt
    std::ifstream statm("/proc/self/statm");
    if (statm.is_open()) {
        uint64_t vmPages = 0;
        uint64_t rssPages = 0;
        statm >> vmPages >> rssPages;
        long pageSize = sysconf(_SC_PAGESIZE);
        if (pageSize > 0) {
            stats.processVmSizeMb = (vmPages * pageSize) / (1024 * 1024);
            stats.processRssMb = (rssPages * pageSize) / (1024 * 1024);
        }
    }

    stats.isCritical = (stats.availableRamMb < 450);
    return stats;
}

uint64_t MemoryManager::getAvailableRamMb() {
    return queryMemoryStats().availableRamMb;
}

uint64_t MemoryManager::getProcessRssMb() {
    return queryMemoryStats().processRssMb;
}

bool MemoryManager::isMemoryUnderThreshold(uint64_t thresholdMb) {
    uint64_t avail = getAvailableRamMb();
    return (avail < thresholdMb);
}

void MemoryManager::registerMmapRegion(void* address, size_t size, int numLayers) {
    m_mmapBaseAddress = address;
    m_mmapTotalSize = size;
    m_numLayers = std::max(1, numLayers);
    m_bytesPerLayer = size / m_numLayers;
    m_streamingEnabled.store(true);
    m_lastActiveLayer = -1;

    LOGI("Mmap region registered: %p, size: %zu MB across %d layers (~%zu MB/layer)",
         address, size / (1024 * 1024), numLayers, m_bytesPerLayer / (1024 * 1024));
}

void MemoryManager::unregisterMmapRegion() {
    m_streamingEnabled.store(false);
    m_mmapBaseAddress = nullptr;
    m_mmapTotalSize = 0;
    m_numLayers = 0;
    m_bytesPerLayer = 0;
    m_lastActiveLayer = -1;
}

void MemoryManager::adviseLayerActive(int layerIndex) {
    if (!m_streamingEnabled.load() || !m_mmapBaseAddress || layerIndex < 0 || layerIndex >= m_numLayers) {
        return;
    }

    // Prefetch upcoming layer into physical memory
    uint8_t* ptr = static_cast<uint8_t*>(m_mmapBaseAddress) + (layerIndex * m_bytesPerLayer);
    madvise(ptr, m_bytesPerLayer, MADV_WILLNEED);

    // Evict previous layer if active
    if (m_lastActiveLayer >= 0 && m_lastActiveLayer != layerIndex) {
        adviseLayerDone(m_lastActiveLayer);
    }
    m_lastActiveLayer = layerIndex;
}

void MemoryManager::adviseLayerDone(int layerIndex) {
    if (!m_streamingEnabled.load() || !m_mmapBaseAddress || layerIndex < 0 || layerIndex >= m_numLayers) {
        return;
    }

    uint8_t* ptr = static_cast<uint8_t*>(m_mmapBaseAddress) + (layerIndex * m_bytesPerLayer);
    // Advise kernel that pages are no longer actively needed in RAM
    // MADV_DONTNEED tells Linux page cache that pages can be discarded immediately
    madvise(ptr, m_bytesPerLayer, MADV_DONTNEED);
}

bool MemoryManager::maybePruneKvCache(
    struct llama_context* ctx,
    int& currentPos,
    int keepPromptTokens,
    int targetReductionTokens
) {
    if (!ctx) return false;

    MemoryStats stats = queryMemoryStats();
    // Trigger aggressive pruning if free RAM drops below 550 MB or is nearing critical 450 MB
    if (stats.availableRamMb > 550) {
        return false;
    }

    if (currentPos <= keepPromptTokens + targetReductionTokens + 16) {
        // Not enough context tokens to safely prune
        return false;
    }

    LOGW("Memory near threshold (%llu MB free, RSS %llu MB)! Pruning KV Cache...",
         stats.availableRamMb, stats.processRssMb);

    // Context sliding window pruning:
    // Retain initial system prompt tokens: [0, keepPromptTokens)
    // Remove oldest dialogue context: [keepPromptTokens, keepPromptTokens + targetReductionTokens)
    // Shift subsequent tokens left by targetReductionTokens
    int p0 = keepPromptTokens;
    int p1 = keepPromptTokens + targetReductionTokens;
    int delta = -targetReductionTokens;

    // Remove KV cache range
    llama_kv_cache_seq_rm(ctx, 0, p0, p1);

    // Shift the remaining tokens in the sequence backwards
    llama_kv_cache_seq_add(ctx, 0, p1, currentPos, delta);

    currentPos += delta;

    LOGI("KV Cache pruned successfully. Target reduction: %d tokens. New pos: %d",
         targetReductionTokens, currentPos);
    return true;
}

} // namespace privtellm
