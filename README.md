# PrivteLLM: Production-Ready Local Quantized LLM Runtime & OpenAI API Server for Android

**PrivteLLM** is an edge-first, production-ready Android application architected in **Modern C++20 (NDK)** and **Kotlin / Jetpack Compose**. It runs quantized Large Language Models (LLMs) locally on Android devices, exposes a high-performance **OpenAI-compatible embedded API server** (`http://0.0.0.0:8080`), and features real-time dynamic memory adaptation to safely run models from 0.36B up to 20B parameters without triggering Android Low Memory Killer (LMK) crashes.

---

## Architecture Overview

```
+-----------------------------------------------------------------------------------+
|                            PrivteLLM Android System                               |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  +--------------------------------+   +----------------------------------------+  |
|  |     Jetpack Compose UI         |   |    Embedded Ktor Server (CIO Engine)   |  |
|  | - Hardware RAM Meter (Visual)  |   | - Listening on http://0.0.0.0:8080     |  |
|  | - API Server Toggle & LAN IP   |   | - GET  /v1/models                      |  |
|  | - Built-in Model Hub (0.5B-20B)|   | - POST /v1/chat/completions            |  |
|  | - Dynamic [RECOMMENDED] Badge  |   |   • Standard JSON Response             |  |
|  | - Interactive Chat Playground  |   |   • Server-Sent Events (SSE) Streaming |  |
|  +---------------+----------------+   +-------------------+--------------------+  |
|                  |                                        |                       |
|  +---------------v----------------------------------------v--------------------+  |
|  |                 Core Safety & Hardware Profiling Layer                      |  |
|  |  • DeviceProfiler.kt: Real-time ActivityManager RAM & Hardware Tiering      |  |
|  |  • MemoryWatcher.kt: Active 400ms Coroutine Guardrail (<450MB Emergency Stop)|  |
|  +-----------------------------------+-----------------------------------------+  |
|                                      |                                            |
|  +-----------------------------------v-----------------------------------------+  |
|  |                         JNI Bridge (native-lib.cpp)                         |  |
|  |  initModel() | generateCompletion() | releaseModel() | stopGeneration()     |  |
|  +-----------------------------------+-----------------------------------------+  |
|                                      |                                            |
|  +-----------------------------------v-----------------------------------------+  |
|  |                   Native Inference Core (C++20 & NDK)                       |  |
|  |  • LlamaEngine: Tokenizer, Context Management, Autoregressive Decode        |  |
|  |  • Accelerators: ARM64 NEON Vectorization, OpenMP Multithreading, Vulkan    |  |
|  |  • Mmap Layer Streamer: Dynamic madvise(MADV_WILLNEED / MADV_DONTNEED)       |  |
|  |  • Dynamic KV-Cache Pruner: Context sliding window on memory pressure       |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## Key Modules & Deliverables

### 1. Native Core (C++20 & NDK)
- **`CMakeLists.txt`** (`app/src/main/cpp/CMakeLists.txt`):
  - Targets Android ABI `arm64-v8a` with `-O3`, `-march=armv8-a+simd+crypto`, `-fopenmp`, and Vulkan GPU acceleration (`GGML_USE_VULKAN`).
  - Strict C++20 standard compliance with automatic linking to `log`, `android`, `vulkan`, and OpenMP runtime.
- **`native-lib.cpp`** (`app/src/main/cpp/native-lib.cpp`):
  - Clean JNI exports for `initModel`, `generateCompletion`, `releaseModel`, `stopGeneration`, `isModelLoaded`, and `getNativeMemoryStats`.
  - Global JNI reference management for thread-safe asynchronous token streaming callbacks (`onToken`, `onComplete`, `onError`).
- **`memory_manager.cpp` / `llama_engine.cpp`**:
  - **Memory-Conscious Layer Streaming (`mmap = true`)**: Leverages `mmap` with POSIX `madvise` (`MADV_WILLNEED` and `MADV_DONTNEED`) to stream weights per transformer block from flash storage into RAM selectively, dropping inactive layer pages as execution progresses.
  - **Dynamic KV-Cache Pruning**: When device free RAM nears the critical threshold, automatically prunes inactive KV-caches and slides dialogue history while preserving system prompt tokens.

### 2. Hardware Profiler (`DeviceProfiler.kt`)
- Queries `ActivityManager.MemoryInfo` and `/proc/meminfo` in real time.
- Emits a reactive `Flow<MemorySnapshot>` displaying Total RAM, Used RAM, Free RAM, and Memory Pressure (`NORMAL`, `MODERATE`, `CRITICAL`).
- **Dynamic Recommendation Tiers**:
  | Device RAM | Recommended Model Size | Engine Configuration |
  | :--- | :--- | :--- |
  | **< 4 GB RAM** | **≤ 1B Models** (e.g., Qwen-0.5B, SmolLM-360M) | Minimal KV cache, aggressive pruning |
  | **4 GB – 6 GB RAM** | **1B – 3B Models** (e.g., Llama-3.2-1B/3B, Gemma-2-2B) | **Hybrid mmap streaming enabled** |
  | **8 GB – 12 GB RAM** | **7B – 8B Models** (4-bit `Q4_K_M`) | High performance NEON / Vulkan compute |
  | **≥ 16 GB RAM** | **14B – 20B Models** (Quantized `Q4_0`, `Q3_K_M`) | Flagship workstation class reasoning |

### 3. Built-in Model Hub (`ModelCatalogScreen.kt` & `ModelDownloader.kt`)
- Curated list of GGUF models ranging from **0.36B up to 19.5B parameters**.
- Dynamically displays the **`★ [RECOMMENDED FOR YOUR DEVICE]`** badge based on real-time device profiling.
- **Resumable Downloader**: OkHttp with HTTP `Range` headers, chunked streaming, transfer speed calculation (MB/s), SHA-256 hash verification, and atomic file replacement in internal storage (`/data/user/0/com.privtellm/files/models/`).

### 4. Embedded Local API Server (`LocalAiServer.kt`)
- Runs as an Android **Foreground Service** with an ongoing notification and `PARTIAL_WAKE_LOCK`.
- Uses **Ktor CIO Embedded Server** listening on `http://0.0.0.0:8080`.
- **OpenAI-Compatible Endpoints**:
  - `GET /v1/models`: Enumerates installed models and current active model.
  - `POST /v1/chat/completions`:
    - Standard non-streaming JSON completion.
    - Server-Sent Events (`stream: true`) SSE streaming: `data: {"id":"chatcmpl-...","choices":[{"delta":{"content":"..."}}]}\n\n` followed by `data: [DONE]\n\n`.
  - Automatic ChatML template formatting (`<|im_start|>user...<|im_end|>`).
  - Wide CORS support enabling local network access from laptops, PCs, browser apps, and OpenAI SDKs.

### 5. Safety & Memory Guardrails (`MemoryWatcher.kt`)
- Continuous background coroutine monitoring available RAM every 400ms.
- **Critical Threshold (450 MB)**:
  - Intercepts incoming HTTP requests and returns HTTP `429 / 503` before admission.
  - Aborts in-flight native generation loops immediately if RAM drops below 450 MB, preventing the Android OS Low Memory Killer (LMK) from killing the process.

### 6. Jetpack Compose UI (`MainActivity.kt`)
- **Dashboard Screen**:
  - Real-time Hardware RAM Meter showing total, used, free RAM and the 450 MB critical line mark.
  - Server Status Card with toggle switch, local LAN IP address (`http://192.168.x.x:8080`), request counters, and one-tap cURL command copier.
  - Active Loaded Model overview.
- **Model Store Screen**:
  - Download, pause, resume, verify, delete, and load controls for curated models.
  - Dynamic recommendation badges.
- **Playground Screen**:
  - Direct on-device chat with live token streaming and real-time Tokens Per Second (TPS) speedometer.

---

## Testing the OpenAI API Server

Once the server is toggled **ON** in the app, any device on the same Wi-Fi network (or the phone itself via localhost) can issue OpenAI-compatible requests.

### 1. List Available Models
```bash
curl http://<PHONE_IP>:8080/v1/models
```
**Response:**
```json
{
  "object": "list",
  "data": [
    {
      "id": "llama-3.2-1b-instruct-q4_k_m",
      "object": "model",
      "created": 1718000000,
      "owned_by": "privtellm-ondevice"
    }
  ]
}
```

### 2. Stream Chat Completions (SSE)
```bash
curl -N -X POST http://<PHONE_IP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama-3.2-1b-instruct-q4_k_m",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant running locally on Android."},
      {"role": "user", "content": "Explain why quantized LLMs run efficiently on ARM NEON."}
    ],
    "temperature": 0.7,
    "stream": true
  }'
```
**Stream Output:**
```
data: {"id":"chatcmpl-a1b2c3d4","object":"chat.completion.chunk","created":1718000001,"model":"llama-3.2-1b-instruct-q4_k_m","choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}

data: {"id":"chatcmpl-a1b2c3d4","object":"chat.completion.chunk","created":1718000001,"model":"llama-3.2-1b-instruct-q4_k_m","choices":[{"index":0,"delta":{"content":"Quantized"}}]}

data: {"id":"chatcmpl-a1b2c3d4","object":"chat.completion.chunk","created":1718000001,"model":"llama-3.2-1b-instruct-q4_k_m","choices":[{"index":0,"delta":{"content":" models"}}]}

...

data: {"id":"chatcmpl-a1b2c3d4","object":"chat.completion.chunk","created":1718000001,"model":"llama-3.2-1b-instruct-q4_k_m","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

### 3. Using the Official Python `openai` SDK
```python
from openai import OpenAI

client = OpenAI(
    base_url="http://<PHONE_IP>:8080/v1",
    api_key="local-not-required"
)

response = client.chat.completions.create(
    model="llama-3.2-1b-instruct-q4_k_m",
    messages=[
        {"role": "user", "content": "Write a fast matrix multiplication function in C++."}
    ],
    stream=True
)

for chunk in response:
    content = chunk.choices[0].delta.content or ""
    print(content, end="", flush=True)
print()
```

---

## Directory Structure

```
/workspace/privtellm
├── app/
│   ├── build.gradle.kts                 # AGP, NDK C++20, Compose, Ktor, OkHttp, Kotlinx
│   ├── proguard-rules.pro               # JNI, Ktor, and Serialization keep rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # Foreground service, cleartext HTTP, permissions
│       │   ├── cpp/                     # Modern C++20 Native Engine
│       │   │   ├── CMakeLists.txt       # ARM64-v8a NEON, OpenMP, Vulkan build config
│       │   │   ├── native-lib.cpp       # JNI exports & async callback bridge
│       │   │   ├── llama_engine.h       # Engine interface & generation callback
│       │   │   ├── llama_engine.cpp     # Inference loop, sampling, memory guardrails
│       │   │   ├── memory_manager.h     # System RAM query, mmap streaming, KV pruner
│       │   │   ├── memory_manager.cpp   # proc/meminfo & madvise implementation
│       │   │   └── include/
│       │   │       ├── llama.h          # Standard llama.cpp C-API interface
│       │   │       └── ggml.h           # GGML backend & Vulkan declarations
│       │   ├── java/com/privtellm/
│       │   │   ├── PrivteLLMApp.kt      # Application class & Notification Channel
│       │   │   ├── core/
│       │   │   │   ├── profiler/
│       │   │   │   │   └── DeviceProfiler.kt  # RAM detection & hardware tiering
│       │   │   │   └── safety/
│       │   │   │       └── MemoryWatcher.kt   # 450 MB active OOM guardrail
│       │   │   ├── hub/
│       │   │   │   ├── ModelCatalog.kt        # Curated 0.5B-20B GGUF catalog
│       │   │   │   └── ModelDownloader.kt     # Resumable OkHttp downloader
│       │   │   ├── nativebridge/
│       │   │   │   └── NativeLib.kt           # Kotlin JNI wrapper & Flow generator
│       │   │   ├── server/
│       │   │   │   ├── LocalAiServer.kt       # Ktor CIO foreground service
│       │   │   │   ├── OpenAiModels.kt        # OpenAI request/response data schemas
│       │   │   │   └── NetworkUtils.kt        # LAN IP address resolver
│       │   │   └── ui/
│       │   │       ├── MainActivity.kt        # Compose activity & main tabs
│       │   │       ├── ModelCatalogScreen.kt  # Model store with recommendation badges
│       │   │       ├── ChatPlaygroundScreen.kt# On-device streaming chat & TPS meter
│       │   │       ├── ServerStatusCard.kt    # API server toggle & IP display
│       │   │       ├── RamMeter.kt            # Hardware RAM meter & safety line
│       │   │       └── theme/
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res/
│       │       ├── drawable/
│       │       │   └── ic_launcher_foreground.xml
│       │       ├── mipmap-anydpi-v26/
│       │       │   └── ic_launcher.xml
│       │       ├── values/
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       └── xml/
│       │           └── network_security_config.xml
│       └── test/java/com/privtellm/     # Unit tests
│           ├── DeviceProfilerTest.kt    # Tiering & pressure tests
│           ├── OpenAiSerializationTest.kt# JSON & SSE serialization tests
│           └── ModelCatalogTest.kt      # Catalog integrity tests
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## Building the Project

1. **Prerequisites**:
   - Android Studio Jellyfish (2023.3.1+) or Ladybug (2024.2.1+)
   - Android SDK 34 (API 34)
   - Android NDK `26.1.10909125` or newer
   - CMake 3.22.1+
   - JDK 17

2. **Command Line Build**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
