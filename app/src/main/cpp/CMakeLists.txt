cmake_minimum_required(VERSION 3.22.1)

project("privtellm_native" C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Optimization Flags for ARM64-v8a with NEON SIMD
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_compile_options(
        -O3
        -march=armv8-a+simd
        -fno-finite-math-only
    )
endif()

# Configuration flags for llama.cpp build
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libraries" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP to avoid libomp.so issues" FORCE)
set(LLAMA_OPENMP OFF CACHE BOOL "Disable OpenMP" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "Disable common tools" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable tests" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Disable tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable server binary" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "Disable app binary" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "Disable UI binary" FORCE)
set(LLAMA_STANDALONE OFF CACHE BOOL "Standalone build off" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Disable curl" FORCE)
set(LLAMA_FATAL_WARNINGS OFF CACHE BOOL "Disable fatal warnings" FORCE)
set(GGML_FATAL_WARNINGS OFF CACHE BOOL "Disable fatal warnings" FORCE)

# Add real llama.cpp source subdirectory
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama_src/CMakeLists.txt")
    add_subdirectory(llama_src)
endif()

# Android Logging & System Libraries
find_library(log-lib log)
find_library(android-lib android)

# Include Directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/ggml/include
)

# Source Files
set(NATIVE_SOURCES
    native-lib.cpp
    llama_engine.cpp
    memory_manager.cpp
)

# Build Native Shared Library
add_library(
    privtellm_native
    SHARED
    ${NATIVE_SOURCES}
)

# Link Dependencies: llama statically bundles ggml, ggml-base, and ggml-cpu
target_link_libraries(
    privtellm_native
    llama
    ${log-lib}
    ${android-lib}
)
