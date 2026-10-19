#pragma once

#include <string>
#include <vector>
#include <functional>
#include <memory>
#include <atomic>
#include <mutex>
#include "llama.h"
#include "memory_manager.h"

namespace privtellm {

struct GenerationCallback {
    std::function<void(const std::string& token)> onToken;
    std::function<void(const std::string& fullText, int promptTokens, int completionTokens, double tps)> onComplete;
    std::function<void(int errorCode, const std::string& message)> onError;
};

class LlamaEngine {
public:
    static LlamaEngine& getInstance();

    bool initModel(
        const std::string& modelPath,
        int nThreads,
        int nGpuLayers,
        bool useMmap,
        bool streamingExecution
    );

    bool generateCompletion(
        const std::string& prompt,
        GenerationCallback callback,
        float temperature = 0.7f,
        float topP = 0.9f,
        int maxTokens = 1024
    );

    void stopGeneration();
    void releaseModel();
    bool isLoaded() const;

    MemoryStats getMemoryStats() const;

private:
    LlamaEngine();
    ~LlamaEngine();

    mutable std::recursive_mutex m_engineMutex;
    std::atomic<bool> m_isLoaded{false};
    std::atomic<bool> m_isGenerating{false};
    std::atomic<bool> m_stopRequested{false};

    struct llama_model* m_model = nullptr;
    struct llama_context* m_ctx = nullptr;
    const struct llama_vocab* m_vocab = nullptr;


    std::string m_modelPath;
    int m_nThreads = 4;
    int m_nGpuLayers = 0;
    bool m_useMmap = true;
    bool m_streamingExecution = false;
    int m_nLayers = 0;
};

} // namespace privtellm
