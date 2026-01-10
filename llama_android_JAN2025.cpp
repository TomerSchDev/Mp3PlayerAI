// app/src/main/cpp/llama_android.cpp
// JNI wrapper for llama.cpp on Android
// Updated for llama.cpp API as of January 2025

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.cpp/include/llama.h"

#define TAG "LlamaAndroid"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

/**
 * Initialize Llama model
 * Returns context pointer (as long)
 */
JNIEXPORT jlong JNICALL
Java_com_tomersch_mp3playerai_ai_LocalLlmInterpreter_initLlama(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath,
        jint nCtx,
        jint nThreads) {
    
    const char *model_path = env->GetStringUTFChars(modelPath, nullptr);
    
    LOGD("Initializing Llama with model: %s", model_path);
    LOGD("Context size: %d, Threads: %d", nCtx, nThreads);
    
    // Initialize backend
    llama_backend_init();
    
    // Load model
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU only
    
    llama_model *model = llama_model_load_from_file(model_path, model_params);
    
    env->ReleaseStringUTFChars(modelPath, model_path);
    
    if (model == nullptr) {
        LOGE("❌ Failed to load model");
        llama_backend_free();
        return 0;
    }
    
    LOGD("Model loaded successfully");
    
    // Create context from model
    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;
    
    // Use new API: llama_init_from_model
    llama_context *ctx = llama_init_from_model(model, ctx_params);
    
    if (ctx == nullptr) {
        LOGE("❌ Failed to create context");
        llama_model_free(model);
        llama_backend_free();
        return 0;
    }
    
    LOGD("✅ Llama initialized successfully");
    
    return reinterpret_cast<jlong>(ctx);
}

/**
 * Generate text from prompt
 */
JNIEXPORT jstring JNICALL
Java_com_tomersch_mp3playerai_ai_LocalLlmInterpreter_generateText(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr,
        jstring prompt,
        jfloat temperature,
        jint maxTokens) {
    
    llama_context *ctx = reinterpret_cast<llama_context*>(contextPtr);
    
    if (ctx == nullptr) {
        LOGE("❌ Invalid context pointer");
        return env->NewStringUTF("");
    }
    
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    size_t prompt_len = strlen(prompt_str);
    
    LOGD("Generating text (prompt_len=%zu, temp=%.2f, max=%d)", 
         prompt_len, temperature, maxTokens);
    
    // Get model from context
    const llama_model *model = llama_get_model(ctx);
    
    // Tokenize prompt
    std::vector<llama_token> tokens_list;
    int n_tokens_max = prompt_len + 512;
    tokens_list.resize(n_tokens_max);
    
    int n_tokens = llama_tokenize(
        model,
        prompt_str,
        (int)prompt_len,
        tokens_list.data(),
        n_tokens_max,
        true,   // add_bos
        false   // special
    );
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    if (n_tokens < 0) {
        n_tokens_max = -n_tokens;
        tokens_list.resize(n_tokens_max);
        n_tokens = llama_tokenize(
            model,
            prompt_str,
            (int)prompt_len,
            tokens_list.data(),
            n_tokens_max,
            true,
            false
        );
        if (n_tokens < 0) {
            LOGE("❌ Failed to tokenize");
            return env->NewStringUTF("");
        }
    }
    
    tokens_list.resize(n_tokens);
    LOGD("Tokenized: %d tokens", n_tokens);
    
    // Clear KV cache
    llama_kv_cache_clear(ctx);
    
    // Decode prompt
    llama_batch batch = llama_batch_get_one(tokens_list.data(), n_tokens);
    
    if (llama_decode(ctx, batch) != 0) {
        LOGE("❌ Failed to decode prompt");
        return env->NewStringUTF("");
    }
    
    LOGD("Prompt decoded, generating...");
    
    // Initialize sampler
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));
    
    // Generate tokens
    std::string result;
    int n_generated = 0;
    
    while (n_generated < maxTokens) {
        // Sample next token
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);
        
        // Check for EOS
        if (llama_vocab_is_eog(model, new_token)) {
            LOGD("EOS token");
            break;
        }
        
        // Decode token
        char piece[256];
        int n_chars = llama_vocab_token_to_piece(
            model, new_token, piece, sizeof(piece), 0, false
        );
        
        if (n_chars < 0) {
            LOGE("Failed to decode token");
            break;
        }
        
        result.append(piece, n_chars);
        
        // Early stop for complete JSON
        if (result.length() > 30 && result.find('}') != std::string::npos) {
            size_t open = result.find('{');
            size_t close = result.find('}');
            if (open != std::string::npos && close > open) {
                LOGD("Complete JSON detected");
                break;
            }
        }
        
        // Accept token
        llama_sampler_accept(smpl, new_token);
        
        // Prepare next batch
        tokens_list.clear();
        tokens_list.push_back(new_token);
        batch = llama_batch_get_one(tokens_list.data(), 1);
        
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Failed to decode token");
            break;
        }
        
        n_generated++;
        
        if (n_generated % 50 == 0) {
            LOGD("Generated %d tokens", n_generated);
        }
    }
    
    llama_sampler_free(smpl);
    
    LOGD("✅ Generated %d tokens, %zu chars", n_generated, result.length());
    
    return env->NewStringUTF(result.c_str());
}

/**
 * Free Llama context
 */
JNIEXPORT void JNICALL
Java_com_tomersch_mp3playerai_ai_LocalLlmInterpreter_freeLlama(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr) {
    
    llama_context *ctx = reinterpret_cast<llama_context*>(contextPtr);
    
    if (ctx != nullptr) {
        LOGD("Freeing context...");
        
        const llama_model *model = llama_get_model(ctx);
        
        llama_free(ctx);
        llama_model_free(const_cast<llama_model*>(model));
        
        LOGD("✅ Freed");
    }
    
    llama_backend_free();
}

} // extern "C"
