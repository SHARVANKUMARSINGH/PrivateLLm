#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama_engine.h"
#include "memory_manager.h"

#define TAG_JNI "PrivteLLM_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG_JNI, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG_JNI, __VA_ARGS__)

static JavaVM* g_vm = nullptr;

jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    LOGI("JNI_OnLoad called, JavaVM stored.");
    return JNI_VERSION_1_6;
}

// ==============================================================================
// Weak fallback stubs for llama.h C-API to allow compilation both standalone
// and when linking against upstream llama.cpp static/shared libraries.
// ==============================================================================
extern "C" {

__attribute__((weak)) llama_model_params llama_model_default_params(void) {
    llama_model_params p{};
    p.n_gpu_layers = 0;
    p.use_mmap = true;
    p.use_mlock = false;
    return p;
}

__attribute__((weak)) llama_context_params llama_context_default_params(void) {
    llama_context_params p{};
    p.n_ctx = 2048;
    p.n_threads = 4;
    p.n_threads_batch = 4;
    p.flash_attn = true;
    return p;
}

__attribute__((weak)) void llama_backend_init(void) {}
__attribute__((weak)) void llama_backend_free(void) {}

__attribute__((weak)) struct llama_model* llama_load_model_from_file(
    const char* /*path_model*/,
    struct llama_model_params /*params*/
) {
    // Return dummy model pointer if standalone
    return reinterpret_cast<struct llama_model*>(0xDEADBEEF);
}

__attribute__((weak)) void llama_free_model(struct llama_model* /*model*/) {}

__attribute__((weak)) struct llama_context* llama_new_context_with_model(
    struct llama_model* /*model*/,
    struct llama_context_params /*params*/
) {
    return reinterpret_cast<struct llama_context*>(0xCAFEBABE);
}

__attribute__((weak)) void llama_free(struct llama_context* /*ctx*/) {}

__attribute__((weak)) int32_t llama_n_ctx(const struct llama_context* /*ctx*/) {
    return 2048;
}

__attribute__((weak)) int32_t llama_n_vocab(const struct llama_model* /*model*/) {
    return 32000;
}

__attribute__((weak)) int32_t llama_n_layer(const struct llama_model* /*model*/) {
    return 24;
}

__attribute__((weak)) int32_t llama_tokenize(
    const struct llama_model* /*model*/,
    const char* text,
    int32_t text_len,
    llama_token* tokens,
    int32_t n_tokens_max,
    bool /*add_special*/,
    bool /*parse_special*/
) {
    int32_t count = std::min(text_len, n_tokens_max);
    for (int32_t i = 0; i < count; ++i) {
        tokens[i] = static_cast<llama_token>(static_cast<uint8_t>(text[i]));
    }
    return count;
}

__attribute__((weak)) int32_t llama_token_to_piece(
    const struct llama_model* /*model*/,
    llama_token token,
    char* buf,
    int32_t length,
    int32_t /*lstrip*/,
    bool /*special*/
) {
    if (length <= 1) return 0;
    buf[0] = static_cast<char>(token & 0x7F);
    buf[1] = '\0';
    return 1;
}

__attribute__((weak)) llama_token llama_token_bos(const struct llama_model* /*model*/) { return 1; }
__attribute__((weak)) llama_token llama_token_eos(const struct llama_model* /*model*/) { return 2; }
__attribute__((weak)) llama_token llama_token_eot(const struct llama_model* /*model*/) { return 3; }
__attribute__((weak)) bool llama_token_is_eog(const struct llama_model* /*model*/, llama_token token) {
    return token == 2 || token == 3;
}

__attribute__((weak)) llama_batch llama_batch_init(int32_t n_tokens, int32_t /*embd*/, int32_t /*n_seq_max*/) {
    llama_batch b{};
    b.n_tokens = n_tokens;
    b.token = new llama_token[n_tokens];
    b.pos = new llama_pos[n_tokens];
    b.n_seq_id = new int32_t[n_tokens];
    b.seq_id = new llama_seq_id*[n_tokens];
    for (int i = 0; i < n_tokens; ++i) {
        b.seq_id[i] = new llama_seq_id[1]{0};
    }
    b.logits = new int8_t[n_tokens];
    return b;
}

__attribute__((weak)) void llama_batch_free(llama_batch batch) {
    if (batch.seq_id) {
        for (int i = 0; i < batch.n_tokens; ++i) {
            delete[] batch.seq_id[i];
        }
        delete[] batch.seq_id;
    }
    delete[] batch.token;
    delete[] batch.pos;
    delete[] batch.n_seq_id;
    delete[] batch.logits;
}

__attribute__((weak)) int32_t llama_decode(struct llama_context* /*ctx*/, llama_batch /*batch*/) {
    return 0;
}

__attribute__((weak)) void llama_kv_cache_clear(struct llama_context* /*ctx*/) {}

__attribute__((weak)) bool llama_kv_cache_seq_rm(
    struct llama_context* /*ctx*/,
    llama_seq_id /*seq_id*/,
    llama_pos /*p0*/,
    llama_pos /*p1*/
) {
    return true;
}

__attribute__((weak)) void llama_kv_cache_seq_add(
    struct llama_context* /*ctx*/,
    llama_seq_id /*seq_id*/,
    llama_pos /*p0*/,
    llama_pos /*p1*/,
    llama_pos /*delta*/
) {}

static float dummy_logits[32000] = {0};
__attribute__((weak)) float* llama_get_logits_ith(struct llama_context* /*ctx*/, int32_t /*i*/) {
    return dummy_logits;
}

__attribute__((weak)) llama_token llama_sample_token_greedy(
    struct llama_context* /*ctx*/,
    llama_token_data_array* /*candidates*/
) {
    static int counter = 0;
    const char* sample_words[] = {" The", " quick", " brown", " fox", " jumps", " over", " lazy", " dog", "."};
    int idx = (counter++) % 9;
    return static_cast<llama_token>(sample_words[idx][1]);
}

__attribute__((weak)) llama_token llama_sample_token(
    struct llama_context* ctx,
    llama_token_data_array* candidates
) {
    return llama_sample_token_greedy(ctx, candidates);
}

__attribute__((weak)) void llama_sample_temp(
    struct llama_context* /*ctx*/,
    llama_token_data_array* /*candidates*/,
    float /*temp*/
) {}

__attribute__((weak)) void llama_sample_top_p(
    struct llama_context* /*ctx*/,
    llama_token_data_array* /*candidates*/,
    float /*p*/,
    size_t /*min_keep*/
) {}

__attribute__((weak)) void ggml_backend_vk_init(void) {
    LOGI("Vulkan backend init requested.");
}
__attribute__((weak)) void ggml_backend_vk_free(void) {}
__attribute__((weak)) bool ggml_backend_vk_available(void) { return true; }

} // extern "C"

// ==============================================================================
// JNI Bridge Exports for com.privtellm.nativebridge.NativeLib
// ==============================================================================
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_privtellm_nativebridge_NativeLib_initModel(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring path,
    jint nThreads,
    jint nGpuLayers,
    jboolean mmap,
    jboolean streamingExecution
) {
    if (!path) return JNI_FALSE;

    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    std::string modelPathStr(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);

    LOGI("JNI initModel called with path: %s", modelPathStr.c_str());

    bool result = privtellm::LlamaEngine::getInstance().initModel(
        modelPathStr,
        static_cast<int>(nThreads),
        static_cast<int>(nGpuLayers),
        static_cast<bool>(mmap),
        static_cast<bool>(streamingExecution)
    );

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_privtellm_nativebridge_NativeLib_generateCompletion(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring prompt,
    jobject listener
) {
    if (!prompt || !listener) {
        LOGE("generateCompletion called with null prompt or listener");
        return JNI_FALSE;
    }

    const char* nativePrompt = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(nativePrompt);
    env->ReleaseStringUTFChars(prompt, nativePrompt);

    // Create a global reference to listener object to invoke across thread boundaries safely
    jobject globalListener = env->NewGlobalRef(listener);
    jclass listenerClass = env->GetObjectClass(listener);
    jmethodID onTokenMethod = env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(listenerClass, "onComplete", "(Ljava/lang/String;IID)V");
    jmethodID onErrorMethod = env->GetMethodID(listenerClass, "onError", "(ILjava/lang/String;)V");

    privtellm::GenerationCallback callback;

    callback.onToken = [globalListener, onTokenMethod](const std::string& token) {
        if (!g_vm) return;
        JNIEnv* localEnv = nullptr;
        bool attached = false;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&localEnv), JNI_VERSION_1_6) != JNI_OK) {
            g_vm->AttachCurrentThread(&localEnv, nullptr);
            attached = true;
        }

        if (localEnv && onTokenMethod) {
            jstring jToken = localEnv->NewStringUTF(token.c_str());
            localEnv->CallVoidMethod(globalListener, onTokenMethod, jToken);
            localEnv->DeleteLocalRef(jToken);
        }

        if (attached) {
            g_vm->DetachCurrentThread();
        }
    };

    callback.onComplete = [globalListener, onCompleteMethod](
        const std::string& fullText,
        int promptTokens,
        int completionTokens,
        double tps
    ) {
        if (!g_vm) return;
        JNIEnv* localEnv = nullptr;
        bool attached = false;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&localEnv), JNI_VERSION_1_6) != JNI_OK) {
            g_vm->AttachCurrentThread(&localEnv, nullptr);
            attached = true;
        }

        if (localEnv && onCompleteMethod) {
            jstring jFull = localEnv->NewStringUTF(fullText.c_str());
            localEnv->CallVoidMethod(
                globalListener,
                onCompleteMethod,
                jFull,
                static_cast<jint>(promptTokens),
                static_cast<jint>(completionTokens),
                static_cast<jdouble>(tps)
            );
            localEnv->DeleteLocalRef(jFull);
        }

        if (attached) {
            g_vm->DetachCurrentThread();
        }
    };

    callback.onError = [globalListener, onErrorMethod](int errorCode, const std::string& message) {
        if (!g_vm) return;
        JNIEnv* localEnv = nullptr;
        bool attached = false;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&localEnv), JNI_VERSION_1_6) != JNI_OK) {
            g_vm->AttachCurrentThread(&localEnv, nullptr);
            attached = true;
        }

        if (localEnv && onErrorMethod) {
            jstring jMsg = localEnv->NewStringUTF(message.c_str());
            localEnv->CallVoidMethod(globalListener, onErrorMethod, static_cast<jint>(errorCode), jMsg);
            localEnv->DeleteLocalRef(jMsg);
        }

        if (attached) {
            g_vm->DetachCurrentThread();
        }
    };

    bool success = privtellm::LlamaEngine::getInstance().generateCompletion(promptStr, callback);

    env->DeleteGlobalRef(globalListener);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_privtellm_nativebridge_NativeLib_releaseModel(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    LOGI("JNI releaseModel called");
    privtellm::LlamaEngine::getInstance().releaseModel();
}

JNIEXPORT void JNICALL
Java_com_privtellm_nativebridge_NativeLib_stopGeneration(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    LOGI("JNI stopGeneration called");
    privtellm::LlamaEngine::getInstance().stopGeneration();
}

JNIEXPORT jboolean JNICALL
Java_com_privtellm_nativebridge_NativeLib_isModelLoaded(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return privtellm::LlamaEngine::getInstance().isLoaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL
Java_com_privtellm_nativebridge_NativeLib_getNativeMemoryStats(
    JNIEnv* env,
    jobject /*thiz*/
) {
    privtellm::MemoryStats stats = privtellm::LlamaEngine::getInstance().getMemoryStats();
    jlongArray result = env->NewLongArray(4);
    if (!result) return nullptr;

    jlong data[4] = {
        static_cast<jlong>(stats.totalRamMb),
        static_cast<jlong>(stats.availableRamMb),
        static_cast<jlong>(stats.processRssMb),
        static_cast<jlong>(stats.processVmSizeMb)
    };
    env->SetLongArrayRegion(result, 0, 4, data);
    return result;
}

} // extern "C"
