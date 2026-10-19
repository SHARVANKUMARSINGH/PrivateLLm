#pragma once

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// GGML Backend and Types
typedef enum ggml_type {
    GGML_TYPE_F32  = 0,
    GGML_TYPE_F16  = 1,
    GGML_TYPE_Q4_0 = 2,
    GGML_TYPE_Q4_1 = 3,
    GGML_TYPE_Q5_0 = 6,
    GGML_TYPE_Q5_1 = 7,
    GGML_TYPE_Q8_0 = 8,
    GGML_TYPE_Q8_1 = 9,
    GGML_TYPE_Q2_K = 10,
    GGML_TYPE_Q3_K = 11,
    GGML_TYPE_Q4_K = 12,
    GGML_TYPE_Q5_K = 13,
    GGML_TYPE_Q6_K = 14,
    GGML_TYPE_COUNT,
} ggml_type;

typedef enum ggml_backend_type {
    GGML_BACKEND_TYPE_CPU = 0,
    GGML_BACKEND_TYPE_GPU = 10,
    GGML_BACKEND_TYPE_GPU_SPLIT = 20,
} ggml_backend_type;

struct ggml_context;
struct ggml_tensor;

// Vulkan Acceleration Hooks
void ggml_backend_vk_init(void);
void ggml_backend_vk_free(void);
bool ggml_backend_vk_available(void);

#ifdef __cplusplus
}
#endif
