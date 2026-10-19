#pragma once

#include "ggml.h"
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef int32_t llama_pos;
typedef int32_t llama_token;
typedef int32_t llama_seq_id;

struct llama_model;
struct llama_context;

typedef struct llama_token_data {
    llama_token id;
    float logit;
    float p;
} llama_token_data;

typedef struct llama_token_data_array {
    llama_token_data * data;
    size_t size;
    bool sorted;
} llama_token_data_array;

typedef struct llama_batch {
    int32_t n_tokens;
    llama_token  * token;
    float        * embd;
    llama_pos    * pos;
    int32_t      * n_seq_id;
    llama_seq_id ** seq_id;
    int8_t       * logits;

    // memory allocated
    int32_t n_tokens_alloc;
} llama_batch;

typedef struct llama_model_params {
    int32_t n_gpu_layers;
    int32_t main_gpu;
    const float * tensor_split;
    bool vocab_only;
    bool use_mmap;
    bool use_mlock;
    bool check_tensors;
} llama_model_params;

typedef struct llama_context_params {
    uint32_t seed;
    uint32_t n_ctx;
    uint32_t n_batch;
    uint32_t n_ubatch;
    uint32_t n_seq_max;
    uint32_t n_threads;
    uint32_t n_threads_batch;
    int8_t   rope_scaling_type;
    int32_t  pooling_type;
    float    rope_freq_base;
    float    rope_freq_scale;
    float    yarn_ext_factor;
    float    yarn_attn_factor;
    float    yarn_beta_fast;
    float    yarn_beta_slow;
    uint32_t yarn_orig_ctx;
    float    defrag_thold;
    bool     embeddings;
    bool     offload_kqv;
    bool     flash_attn;
    enum ggml_type type_k;
    enum ggml_type type_v;
} llama_context_params;

// Core functions
llama_model_params   llama_model_default_params(void);
llama_context_params llama_context_default_params(void);

void llama_backend_init(void);
void llama_backend_free(void);

struct llama_model * llama_load_model_from_file(
    const char * path_model,
    struct llama_model_params params
);

void llama_free_model(struct llama_model * model);

struct llama_context * llama_new_context_with_model(
    struct llama_model * model,
    struct llama_context_params params
);

void llama_free(struct llama_context * ctx);

int32_t llama_n_ctx(const struct llama_context * ctx);
int32_t llama_n_vocab(const struct llama_model * model);
int32_t llama_n_layer(const struct llama_model * model);

// Tokenization
int32_t llama_tokenize(
    const struct llama_model * model,
    const char * text,
    int32_t text_len,
    llama_token * tokens,
    int32_t n_tokens_max,
    bool add_special,
    bool parse_special
);

int32_t llama_token_to_piece(
    const struct llama_model * model,
    llama_token token,
    char * buf,
    int32_t length,
    int32_t lstrip,
    bool special
);

llama_token llama_token_bos(const struct llama_model * model);
llama_token llama_token_eos(const struct llama_model * model);
llama_token llama_token_eot(const struct llama_model * model);
bool llama_token_is_eog(const struct llama_model * model, llama_token token);

// Batch & Decoding
llama_batch llama_batch_init(int32_t n_tokens, int32_t embd, int32_t n_seq_max);
void llama_batch_free(llama_batch batch);
int32_t llama_decode(struct llama_context * ctx, llama_batch batch);

// KV Cache Management
void llama_kv_cache_clear(struct llama_context * ctx);
bool llama_kv_cache_seq_rm(
    struct llama_context * ctx,
    llama_seq_id seq_id,
    llama_pos p0,
    llama_pos p1
);
void llama_kv_cache_seq_add(
    struct llama_context * ctx,
    llama_seq_id seq_id,
    llama_pos p0,
    llama_pos p1,
    llama_pos delta
);

// Sampling
float * llama_get_logits_ith(struct llama_context * ctx, int32_t i);
llama_token llama_sample_token_greedy(
    struct llama_context * ctx,
    llama_token_data_array * candidates
);
llama_token llama_sample_token(
    struct llama_context * ctx,
    llama_token_data_array * candidates
);
void llama_sample_temp(
    struct llama_context * ctx,
    llama_token_data_array * candidates,
    float temp
);
void llama_sample_top_p(
    struct llama_context * ctx,
    llama_token_data_array * candidates,
    float p,
    size_t min_keep
);

#ifdef __cplusplus
}
#endif
