#include "llama_engine.h"
#include <chrono>
#include <thread>
#include <cstring>
#include <cmath>
#include <vector>
#include <algorithm>

#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

namespace privtellm {

LlamaEngine& LlamaEngine::getInstance() {
    static LlamaEngine instance;
    return instance;
}

LlamaEngine::LlamaEngine() {
    llama_backend_init();
}

LlamaEngine::~LlamaEngine() {
    releaseModel();
    llama_backend_free();
}

bool LlamaEngine::initModel(
    const std::string& modelPath,
    int nThreads,
    int nGpuLayers,
    bool useMmap,
    bool streamingExecution
) {
    std::lock_guard<std::recursive_mutex> lock(m_engineMutex);

    if (m_isLoaded.load()) {
        LOGI("Releasing previously loaded model before loading new model.");
        releaseModel();
    }

    uint64_t availRam = MemoryManager::getInstance().getAvailableRamMb();
    LOGI("Init real llama.cpp model: %s, threads=%d, gpuLayers=%d, mmap=%d. Avail RAM: %llu MB",
         modelPath.c_str(), nThreads, nGpuLayers, useMmap, availRam);

    m_modelPath = modelPath;
    m_nThreads = (nThreads > 0) ? nThreads : 4;
    m_nGpuLayers = nGpuLayers;
    m_useMmap = useMmap;
    m_streamingExecution = streamingExecution;

    // Setup Model Parameters
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;
    model_params.check_tensors = false;

    // Load Model using modern llama.cpp API
    m_model = llama_model_load_from_file(modelPath.c_str(), model_params);
    if (!m_model) {
        LOGE("Failed to load real llama model from file: %s", modelPath.c_str());
        return false;
    }

    m_vocab = llama_model_get_vocab(m_model);
    m_nLayers = llama_model_n_layer(m_model);
    LOGI("Real model loaded successfully! Total transformer layers: %d", m_nLayers);

    // Setup Dynamic Layer Streaming if requested
    if (streamingExecution && useMmap && m_model) {
        MemoryManager::getInstance().registerMmapRegion(
            reinterpret_cast<void*>(m_model),
            1024ULL * 1024ULL * 1024ULL,
            m_nLayers
        );
    }

    // Setup Context Parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; // Safe default for mobile edge devices
    ctx_params.n_batch = 512;
    ctx_params.n_threads = m_nThreads;
    ctx_params.n_threads_batch = m_nThreads;
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;

    m_ctx = llama_init_from_model(m_model, ctx_params);
    if (!m_ctx) {
        LOGE("Failed to create llama context from loaded model");
        llama_model_free(m_model);
        m_model = nullptr;
        m_vocab = nullptr;
        return false;
    }

    m_isLoaded.store(true);
    m_stopRequested.store(false);
    LOGI("Real Llama context created and ready for inference.");
    return true;
}

bool LlamaEngine::generateCompletion(
    const std::string& prompt,
    GenerationCallback callback,
    float temperature,
    float topP,
    int maxTokens
) {
    if (!m_isLoaded.load() || !m_model || !m_ctx || !m_vocab) {
        if (callback.onError) {
            callback.onError(500, "Model is not loaded");
        }
        return false;
    }

    // Guardrail Check before starting inference
    if (MemoryManager::getInstance().isMemoryUnderThreshold(350)) {
        uint64_t avail = MemoryManager::getInstance().getAvailableRamMb();
        std::string err = "Inference aborted: Free RAM (" + std::to_string(avail) +
                          " MB) is below critical 350 MB threshold.";
        LOGE("%s", err.c_str());
        if (callback.onError) {
            callback.onError(429, err);
        }
        return false;
    }

    m_isGenerating.store(true);
    m_stopRequested.store(false);

    auto startTime = std::chrono::steady_clock::now();

    // 1. Tokenize prompt with llama_vocab
    int nPromptTokens = -llama_tokenize(
        m_vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.length()),
        nullptr,
        0,
        true,
        true
    );

    if (nPromptTokens <= 0) {
        nPromptTokens = static_cast<int>(prompt.length()) + 16;
    }

    std::vector<llama_token> tokens(nPromptTokens);
    int nTokens = llama_tokenize(
        m_vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.length()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true
    );

    if (nTokens < 0) {
        tokens.resize(-nTokens);
        nTokens = llama_tokenize(
            m_vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.length()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
        );
    }
    tokens.resize(std::max(0, nTokens));

    int promptTokens = nTokens;
    int completionTokens = 0;
    std::string fullCompletion;

    // Reset KV cache for fresh generation
    llama_memory_t mem = llama_get_memory(m_ctx);
    if (mem) {
        llama_memory_clear(mem, true);
    }

    // 2. Ingest prompt tokens into KV cache
    llama_batch batch = llama_batch_init(512, 0, 1);
    int currentPos = 0;

    for (int i = 0; i < nTokens; i += 512) {
        if (m_stopRequested.load()) {
            llama_batch_free(batch);
            m_isGenerating.store(false);
            if (callback.onError) callback.onError(499, "Generation cancelled by client");
            return false;
        }

        int chunkSize = std::min(512, nTokens - i);
        batch.n_tokens = chunkSize;

        for (int j = 0; j < chunkSize; ++j) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = currentPos + j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (j == chunkSize - 1);
        }

        if (m_streamingExecution) {
            for (int l = 0; l < m_nLayers; ++l) {
                MemoryManager::getInstance().adviseLayerActive(l);
            }
        }

        if (llama_decode(m_ctx, batch) != 0) {
            llama_batch_free(batch);
            m_isGenerating.store(false);
            if (callback.onError) callback.onError(500, "llama_decode failed during prompt processing");
            return false;
        }

        currentPos += chunkSize;
    }

    // 3. Initialize modern llama_sampler chain
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(1337));
    }

    char pieceBuf[256];

    for (int step = 0; step < maxTokens; ++step) {
        if (m_stopRequested.load()) {
            LOGI("Inference stopped cooperatively by request at token step %d", step);
            break;
        }

        // Memory Guardrail during generation loop
        if (MemoryManager::getInstance().isMemoryUnderThreshold(300)) {
            uint64_t avail = MemoryManager::getInstance().getAvailableRamMb();
            LOGE("Critical memory warning during generation: %llu MB left. Aborting native loop.", avail);
            if (callback.onError) {
                callback.onError(429, "Device memory dropped below 300 MB during generation. Safely stopped.");
            }
            break;
        }

        // Context pruning if nearing threshold
        MemoryManager::getInstance().maybePruneKvCache(m_ctx, currentPos, 32, 64);

        // Sample token from logits using real sampler
        llama_token nextToken = llama_sampler_sample(smpl, m_ctx, -1);
        llama_sampler_accept(smpl, nextToken);

        // Check for End-Of-Generation
        if (llama_vocab_is_eog(m_vocab, nextToken)) {
            LOGI("EOG token reached: %d", nextToken);
            break;
        }

        // Convert token to text piece
        std::memset(pieceBuf, 0, sizeof(pieceBuf));
        int nPiece = llama_token_to_piece(m_vocab, nextToken, pieceBuf, sizeof(pieceBuf) - 1, 0, false);
        std::string pieceStr;
        if (nPiece > 0) {
            pieceStr = std::string(pieceBuf, nPiece);
        }

        fullCompletion += pieceStr;
        completionTokens++;

        if (callback.onToken) {
            callback.onToken(pieceStr);
        }

        // Prepare batch for next single token step
        batch.n_tokens = 1;
        batch.token[0] = nextToken;
        batch.pos[0] = currentPos;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;

        if (m_streamingExecution) {
            for (int l = 0; l < m_nLayers; ++l) {
                MemoryManager::getInstance().adviseLayerActive(l);
            }
        }

        if (llama_decode(m_ctx, batch) != 0) {
            LOGE("llama_decode failed at token step %d", step);
            break;
        }

        currentPos++;
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);

    auto endTime = std::chrono::steady_clock::now();
    double elapsedSec = std::chrono::duration<double>(endTime - startTime).count();
    double tps = (elapsedSec > 0.001) ? (completionTokens / elapsedSec) : 0.0;

    LOGI("Generation finished: %d prompt tokens, %d completion tokens in %.2f s (%.2f TPS)",
         promptTokens, completionTokens, elapsedSec, tps);

    if (callback.onComplete) {
        callback.onComplete(fullCompletion, promptTokens, completionTokens, tps);
    }

    m_isGenerating.store(false);
    return true;
}

void LlamaEngine::stopGeneration() {
    LOGI("Stop generation requested.");
    m_stopRequested.store(true);
}

void LlamaEngine::releaseModel() {
    std::lock_guard<std::recursive_mutex> lock(m_engineMutex);

    m_stopRequested.store(true);
    m_isGenerating.store(false);

    MemoryManager::getInstance().unregisterMmapRegion();

    if (m_ctx) {
        llama_free(m_ctx);
    }
    m_ctx = nullptr;

    if (m_model) {
        llama_model_free(m_model);
    }
    m_model = nullptr;
    m_vocab = nullptr;

    m_isLoaded.store(false);
    LOGI("Real model and context released.");
}

bool LlamaEngine::isLoaded() const {
    return m_isLoaded.load();
}

MemoryStats LlamaEngine::getMemoryStats() const {
    return MemoryManager::getInstance().queryMemoryStats();
}

} // namespace privtellm
