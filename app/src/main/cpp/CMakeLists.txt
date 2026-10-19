cmake_minimum_required(VERSION 3.22.1)

project("privtellm_native" C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Optimization Flags for ARM64-v8a with NEON & OpenMP
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_compile_options(
        -O3
        -march=armv8-a+simd+crypto
        -ffast-math
        -flto
        -funroll-loops
    )
    add_link_options(-flto)
endif()

# Vulkan Acceleration for Mobile GPU Offloading
find_library(vulkan-lib vulkan)
if(vulkan-lib)
    message(STATUS "Vulkan library located at: ${vulkan-lib}")
    add_compile_definitions(GGML_USE_VULKAN=1)
else()
    message(WARNING "Vulkan library not found, falling back to CPU NEON execution")
endif()

# Android Logging & System Libraries
find_library(log-lib log)
find_library(android-lib android)

# Include Directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
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

# Link Dependencies
target_link_libraries(
    privtellm_native
    ${log-lib}
    ${android-lib}
    $<$<BOOL:${vulkan-lib}>:${vulkan-lib}>
)
