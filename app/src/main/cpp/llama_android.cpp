#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <cstring>
#include <cstdlib>

#include "llama.h"

#define TAG "LlamaAndroid"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ---------------------------
// Small helpers for "current" llama.cpp API variants
// ---------------------------

static const llama_vocab * get_vocab(const llama_model * model) {
#if defined(LLAMA_API_VERSION) && (LLAMA_API_VERSION >= 1000)
    // Many newer versions expose vocab via llama_model_get_vocab(model)
    return llama_model_get_vocab(model);
#else
    // Older versions sometimes used llama_model_get_vocab as well; if not, you’ll get a compile error here.
    return llama_model_get_vocab(model);
#endif
}

// Some llama.cpp versions use: llama_token_to_piece(vocab, token, buf, size, flags)
// others use: llama_token_to_piece(vocab, token, buf, size, special)
static int token_to_piece_compat(const llama_model * model, llama_token tok, char * buf, int buf_size) {
    const llama_vocab * vocab = get_vocab(model);

#if defined(LLAMA_API_VERSION) && (LLAMA_API_VERSION >= 1000)
    // Signature commonly: llama_token_to_piece(vocab, tok, buf, buf_size, 0, false)
    return llama_token_to_piece(vocab, tok, buf, buf_size, 0, false);
#else
    return llama_token_to_piece(vocab, tok, buf, buf_size, 0, false);
#endif
}

// Tokenize: newer llama.cpp often tokenizes via vocab
static int tokenize_compat(const llama_model * model,
        const char * text,
        int text_len,
        llama_token * out_tokens,
        int max_tokens,
        bool add_bos,
        bool special) {
    const llama_vocab * vocab = get_vocab(model);

#if defined(LLAMA_API_VERSION) && (LLAMA_API_VERSION >= 1000)
    // Common signature: llama_tokenize(vocab, text, text_len, out, max, add_bos, special)
    return llama_tokenize(vocab, text, text_len, out_tokens, max_tokens, add_bos, special);
#else
    return llama_tokenize(vocab, text, text_len, out_tokens, max_tokens, add_bos, special);
#endif
}

// Clear KV cache (your build errors show llama_kv_cache_clear is not available)
// Use seq removal, which exists in modern llama.cpp.
static void kv_clear(llama_context * ctx) {
    // Remove all sequences (seq_id = -1) and all positions
    // Most modern signatures: llama_kv_cache_seq_rm(ctx, seq_id, p0, p1)
    // Using -1 for "all"
    //llama_kv_cache_seq_rm(ctx, 0, -1, -1);
}

// Build a batch from tokens with proper positions
static llama_batch make_batch(const std::vector<llama_token> & tokens, int n_past) {
    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);

    // one sequence id = 0
    for (int i = 0; i < (int)tokens.size(); i++) {
        batch.token[i]    = tokens[i];
        batch.pos[i]      = n_past + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]   = false;
    }

    // We only need logits for the last token
    batch.logits[batch.n_tokens - 1] = 1;
    return batch;
}

struct LlamaHandle {
    llama_model   * model = nullptr;
    llama_context * ctx   = nullptr;
};

extern "C" {

// JNI: long initLlama(String modelPath, int nCtx, int nThreads)
JNIEXPORT jlong JNICALL
Java_com_tomersch_mp3playerai_ai_LocalLlmInterpreter_initLlama(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath,
        jint nCtx,
        jint nThreads) {

    const char * model_path = env->GetStringUTFChars(modelPath, nullptr);

    LOGD("initLlama: model=%s nCtx=%d nThreads=%d", model_path, (int)nCtx, (int)nThreads);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(modelPath, model_path);

    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t)nCtx;
    cparams.n_threads = (int)nThreads;
    cparams.n_threads_batch = (int)nThreads;

    // Modern API replacement for deprecated llama_new_context_with_model
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    auto * handle = new LlamaHandle();
    handle->model = model;
    handle->ctx = ctx;

    LOGD("initLlama OK: handle=%p ctx=%p model=%p", handle, ctx, model);
    return reinterpret_cast<jlong>(handle);
}


// JNI: String generateText(long handlePtr, String prompt, float temperature, int maxTokens)
JNIEXPORT jstring JNICALL
Java_com_tomersch_mp3playerai_ai_LocalLlmInterpreter_generateText(
        JNIEnv *env,
        jobject /* this */,
        jlong handlePtr,
        jstring prompt,
        jfloat temperature,
        jint maxTokens) {

    auto * handle = reinterpret_cast<LlamaHandle*>(handlePtr);
    if (!handle || !handle->ctx || !handle->model) {
        LOGE("generateText: invalid handle");
        return env->NewStringUTF("");
    }

    llama_context * ctx = handle->ctx;
    llama_model   * model = handle->model;

    const char * prompt_c = env->GetStringUTFChars(prompt, nullptr);
    const int prompt_len = (int)std::strlen(prompt_c);

    // Tokenize
    std::vector<llama_token> tokens(prompt_len + 256);
    int n_tok = tokenize_compat(model, prompt_c, prompt_len, tokens.data(), (int)tokens.size(), true, false);
    env->ReleaseStringUTFChars(prompt, prompt_c);

    if (n_tok <= 0) {
        LOGE("tokenize failed");
        return env->NewStringUTF("");
    }
    tokens.resize(n_tok);

    // Reset KV for a fresh single-seq run
    kv_clear(ctx);

    // Decode prompt
    int n_past = 0;
    llama_batch batch = make_batch(tokens, n_past);
    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        LOGE("llama_decode prompt failed");
        return env->NewStringUTF("");
    }
    llama_batch_free(batch);
    n_past += (int)tokens.size();

    // Sampler (modern chain)
    llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(chain_params);

    llama_sampler_chain_add(sampler, llama_sampler_init_temp((float)temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234));

    std::string out;
    out.reserve((size_t)maxTokens * 4);

    // Generate
    for (int i = 0; i < (int)maxTokens; i++) {
        // Sample from last logits (we set logits flag on last token each decode)
        llama_token tok = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(get_vocab(model), tok)) {
            break;
        }

        char piece[512];
        int n_chars = token_to_piece_compat(model, tok, piece, (int)sizeof(piece));
        if (n_chars > 0) {
            out.append(piece, (size_t)n_chars);
        }

        // Early stop if we detect a complete JSON object
        // (Your use case: query parsing / tags)
        size_t open = out.find('{');
        size_t close = out.find('}');
        if (open != std::string::npos && close != std::string::npos && close > open) {
            // Stop at first complete object (you can tighten this later)
            break;
        }

        // Accept token into sampler state (modern accept includes ctx)
        llama_sampler_accept(sampler, tok);

        // Decode next token
        std::vector<llama_token> one = { tok };
        llama_batch b2 = make_batch(one, n_past);

        if (llama_decode(ctx, b2) != 0) {
            llama_batch_free(b2);
            LOGE("llama_decode token failed");
            break;
        }

        llama_batch_free(b2);
        n_past += 1;
    }

    llama_sampler_free(sampler);

    return env->NewStringUTF(out.c_str());
}


// JNI: void freeLlama(long handlePtr)
JNIEXPORT void JNICALL
Java_com_tomersch_mp3playerai_ai_LocalLlmInterpreter_freeLlama(
        JNIEnv *env,
        jobject /* this */,
        jlong handlePtr) {

    auto * handle = reinterpret_cast<LlamaHandle*>(handlePtr);
    if (!handle) return;

    if (handle->ctx) {
        llama_free(handle->ctx);
        handle->ctx = nullptr;
    }
    if (handle->model) {
        llama_model_free(handle->model);
        handle->model = nullptr;
    }

    delete handle;

    // If you might keep multiple contexts/models alive, do NOT backend_free() here.
    // For a single-handle app, it is OK:
    llama_backend_free();
}

} // extern "C"
