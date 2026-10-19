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
