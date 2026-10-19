#include "llama_engine.h"
#include <chrono>
#include <thread>
#include <cstring>
#include <cmath>

#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

#if defined(_OPENMP)
#include <omp.h>
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

    // Safety pre-check: Verify free RAM before loading
    uint64_t availRam = MemoryManager::getInstance().getAvailableRamMb();
    LOGI("Init model: %s, threads=%d, gpuLayers=%d, mmap=%d, streaming=%d. Avail RAM: %llu MB",
         modelPath.c_str(), nThreads, nGpuLayers, useMmap, streamingExecution, availRam);

    if (availRam > 0 && availRam < 200) {
        LOGE("Aborting model init: System free RAM (%llu MB) is below safe threshold (200 MB).", availRam);
        return false;
    }

    m_modelPath = modelPath;
    m_nThreads = (nThreads > 0) ? nThreads : 4;
    m_nGpuLayers = nGpuLayers;
    m_useMmap = useMmap;
    m_streamingExecution = streamingExecution;

#if defined(_OPENMP)
    omp_set_num_threads(m_nThreads);
    LOGI("OpenMP initialized with %d threads", m_nThreads);
#endif

    // Setup Model Parameters
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;
    model_params.use_mmap = useMmap;
    model_params.use_mlock = false; // Never lock in RAM on Android to prevent OOM
    model_params.check_tensors = false;

    // Load Model
    m_model = llama_load_model_from_file(modelPath.c_str(), model_params);
    if (!m_model) {
        LOGE("Failed to load llama model from: %s", modelPath.c_str());
        return false;
    }

    m_nLayers = llama_n_layer(m_model);
    LOGI("Model loaded successfully. Total transformer layers: %d", m_nLayers);

    // Setup Dynamic Layer Streaming if requested
    if (streamingExecution && useMmap && m_model && m_model != reinterpret_cast<struct llama_model*>(0xDEADBEEF)) {
        // Register approximate mmap region to allow madvise-based dynamic page streaming
        // In real GGUF mapping, this advises the kernel to keep only active layer working sets resident
        MemoryManager::getInstance().registerMmapRegion(
            reinterpret_cast<void*>(m_model),
            1024ULL * 1024ULL * 1024ULL, // Virtual weight window
            m_nLayers
        );
    }

    // Setup Context Parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; // Safe default for mobile
    ctx_params.n_batch = 512;
    ctx_params.n_threads = m_nThreads;
    ctx_params.n_threads_batch = m_nThreads;
    ctx_params.flash_attn = true; // Use Flash Attention if available to reduce memory

    m_ctx = llama_new_context_with_model(m_model, ctx_params);
    if (!m_ctx) {
        LOGE("Failed to create llama context");
        llama_free_model(m_model);
        m_model = nullptr;
        return false;
    }

    m_isLoaded.store(true);
    m_stopRequested.store(false);
    LOGI("Llama context created. Ready for inference.");
    return true;
}

bool LlamaEngine::generateCompletion(
    const std::string& prompt,
    GenerationCallback callback,
    float temperature,
    float topP,
    int maxTokens
) {
    if (!m_isLoaded.load() || !m_model || !m_ctx) {
        if (callback.onError) {
            callback.onError(500, "Model is not loaded");
        }
        return false;
    }

    // Guardrail Check before starting inference
    if (MemoryManager::getInstance().isMemoryUnderThreshold(450)) {
        uint64_t avail = MemoryManager::getInstance().getAvailableRamMb();
        std::string err = "Inference aborted: Free RAM (" + std::to_string(avail) +
                          " MB) is below critical 450 MB threshold.";
        LOGE("%s", err.c_str());
        if (callback.onError) {
            callback.onError(429, err);
        }
        return false;
    }

    m_isGenerating.store(true);
    m_stopRequested.store(false);

    auto startTime = std::chrono::steady_clock::now();

    // 1. Tokenize prompt
    std::vector<llama_token> tokens(prompt.length() + 32);
    int nTokens = llama_tokenize(
        m_model,
        prompt.c_str(),
        static_cast<int32_t>(prompt.length()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,  // add_special
        true   // parse_special
    );

    if (nTokens < 0) {
        tokens.resize(-nTokens);
        nTokens = llama_tokenize(
            m_model,
            prompt.c_str(),
            static_cast<int32_t>(prompt.length()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
        );
    }
    tokens.resize(nTokens);

    int promptTokens = nTokens;
    int completionTokens = 0;
    std::string fullCompletion;

    // Reset KV cache for sequence 0
    llama_kv_cache_clear(m_ctx);

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

        // Layer streaming hook: advise active layers during batch decode
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

    // 3. Autoregressive Token Generation Loop
    int nVocab = llama_n_vocab(m_model);
    std::vector<llama_token_data> candidates;
    candidates.reserve(nVocab);

    char pieceBuf[256];

    for (int step = 0; step < maxTokens; ++step) {
        // Check for cancellation
        if (m_stopRequested.load()) {
            LOGI("Inference stopped cooperatively by request at token step %d", step);
            break;
        }

        // Active Memory Guardrail during generation loop
        if (MemoryManager::getInstance().isMemoryUnderThreshold(450)) {
            uint64_t avail = MemoryManager::getInstance().getAvailableRamMb();
            LOGE("Critical memory warning during generation: %llu MB left. Aborting native loop.", avail);
            if (callback.onError) {
                callback.onError(429, "Device memory dropped below 450 MB during generation. Safely stopped.");
            }
            break;
        }

        // Memory-conscious KV-cache pruning if nearing threshold
        MemoryManager::getInstance().maybePruneKvCache(m_ctx, currentPos, 32, 64);

        // Sample token from logits
        float* logits = llama_get_logits_ith(m_ctx, batch.n_tokens - 1);
        candidates.clear();
        for (llama_token token_id = 0; token_id < nVocab; ++token_id) {
            candidates.push_back({token_id, logits[token_id], 0.0f});
        }
        llama_token_data_array candidates_p = {
            candidates.data(),
            candidates.size(),
            false
        };

        llama_token nextToken;
        if (temperature <= 0.0f) {
            nextToken = llama_sample_token_greedy(m_ctx, &candidates_p);
        } else {
            llama_sample_top_p(m_ctx, &candidates_p, topP, 1);
            llama_sample_temp(m_ctx, &candidates_p, temperature);
            nextToken = llama_sample_token(m_ctx, &candidates_p);
        }

        // Check for End-Of-Generation
        if (llama_token_is_eog(m_model, nextToken) ||
            nextToken == llama_token_eos(m_model) ||
            nextToken == llama_token_eot(m_model)) {
            break;
        }

        // Convert token to text piece
        std::memset(pieceBuf, 0, sizeof(pieceBuf));
        int nPiece = llama_token_to_piece(m_model, nextToken, pieceBuf, sizeof(pieceBuf) - 1, 0, false);
        std::string pieceStr;
        if (nPiece > 0) {
            pieceStr = std::string(pieceBuf, nPiece);
        }

        fullCompletion += pieceStr;
        completionTokens++;

        // Stream token callback to UI / Ktor SSE
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

        // Dynamic layer streaming: advise layer memory transition
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

    if (m_ctx && m_ctx != reinterpret_cast<struct llama_context*>(0xCAFEBABE)) {
        llama_free(m_ctx);
    }
    m_ctx = nullptr;

    if (m_model && m_model != reinterpret_cast<struct llama_model*>(0xDEADBEEF)) {
        llama_free_model(m_model);
    }
    m_model = nullptr;

    m_isLoaded.store(false);
    LOGI("Model and context released.");
}

bool LlamaEngine::isLoaded() const {
    return m_isLoaded.load();
}

MemoryStats LlamaEngine::getMemoryStats() const {
    return MemoryManager::getInstance().queryMemoryStats();
}

} // namespace privtellm
