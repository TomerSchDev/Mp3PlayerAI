#include <jni.h>
#include <string>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LLM_JNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LLM_JNI", __VA_ARGS__)

#include "llama.h"

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tomersch_mp3playerai_ai_LlamaLocalClient_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 1024;        // adjust if needed
    cparams.n_threads = 4;       // good start on phone
    g_ctx = llama_new_context_with_model(g_model, cparams);

    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model initialized");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tomersch_mp3playerai_ai_LlamaLocalClient_nativeInfer(
        JNIEnv* env, jobject /*thiz*/, jstring promptJ) {

    if (!g_ctx || !g_model) {
        return env->NewStringUTF("");
    }

    const char* prompt = env->GetStringUTFChars(promptJ, nullptr);

    // Tokenize
    std::string promptStr(prompt);
    env->ReleaseStringUTFChars(promptJ, prompt);

    std::vector<llama_token> tokens(promptStr.size() + 8);
    int n = llama_tokenize(g_model, promptStr.c_str(), (int)promptStr.size(),
            tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) n = 0;
    tokens.resize(n);

    // Evaluate prompt
    if (llama_decode(g_ctx, llama_batch_get_one(tokens.data(), (int)tokens.size(), 0, 0)) != 0) {
        LOGE("decode failed");
        return env->NewStringUTF("");
    }

    // Sampling loop (simple)
    std::string out;
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const int max_new_tokens = 128;

    for (int i = 0; i < max_new_tokens; i++) {
        llama_token tok = llama_sampler_sample(smpl, g_ctx, -1);

        if (tok == llama_token_eos(g_model)) break;

        out += llama_token_to_piece(g_ctx, tok);

        llama_token t = tok;
        if (llama_decode(g_ctx, llama_batch_get_one(&t, 1, llama_get_kv_cache_used_cells(g_ctx), 0)) != 0) {
            LOGE("decode failed during generation");
            break;
        }
    }

    llama_sampler_free(smpl);

    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_tomersch_mp3playerai_ai_LlamaLocalClient_nativeClose(
        JNIEnv* /*env*/, jobject /*thiz*/) {

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;#include <jni.h>
#include <string>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LLM_JNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LLM_JNI", __VA_ARGS__)

#include "llama.h"

        static llama_model* g_model = nullptr;
        static llama_context* g_ctx = nullptr;

        extern "C" JNIEXPORT jboolean JNICALL
        Java_com_tomersch_mp3playerai_ai_LlamaLocalClient_nativeInit(
                JNIEnv* env, jobject /*thiz*/, jstring modelPath) {

            const char* path = env->GetStringUTFChars(modelPath, nullptr);

            llama_backend_init();

            llama_model_params mparams = llama_model_default_params();
            g_model = llama_load_model_from_file(path, mparams);
            env->ReleaseStringUTFChars(modelPath, path);

            if (!g_model) {
                LOGE("Failed to load model");
                return JNI_FALSE;
            }

            llama_context_params cparams = llama_context_default_params();
            cparams.n_ctx = 1024;        // adjust if needed
            cparams.n_threads = 4;       // good start on phone
            g_ctx = llama_new_context_with_model(g_model, cparams);

            if (!g_ctx) {
                LOGE("Failed to create context");
                llama_free_model(g_model);
                g_model = nullptr;
                return JNI_FALSE;
            }

            LOGI("Model initialized");
            return JNI_TRUE;
        }

        extern "C" JNIEXPORT jstring JNICALL
        Java_com_tomersch_mp3playerai_ai_LlamaLocalClient_nativeInfer(
                JNIEnv* env, jobject /*thiz*/, jstring promptJ) {

            if (!g_ctx || !g_model) {
                return env->NewStringUTF("");
            }

            const char* prompt = env->GetStringUTFChars(promptJ, nullptr);

            // Tokenize
            std::string promptStr(prompt);
            env->ReleaseStringUTFChars(promptJ, prompt);

            std::vector<llama_token> tokens(promptStr.size() + 8);
            int n = llama_tokenize(g_model, promptStr.c_str(), (int)promptStr.size(),
                    tokens.data(), (int)tokens.size(), true, true);
            if (n < 0) n = 0;
            tokens.resize(n);

            // Evaluate prompt
            if (llama_decode(g_ctx, llama_batch_get_one(tokens.data(), (int)tokens.size(), 0, 0)) != 0) {
                LOGE("decode failed");
                return env->NewStringUTF("");
            }

            // Sampling loop (simple)
            std::string out;
            llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());

            llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
            llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
            llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.8f));
            llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

            const int max_new_tokens = 128;

            for (int i = 0; i < max_new_tokens; i++) {
                llama_token tok = llama_sampler_sample(smpl, g_ctx, -1);

                if (tok == llama_token_eos(g_model)) break;

                out += llama_token_to_piece(g_ctx, tok);

                llama_token t = tok;
                if (llama_decode(g_ctx, llama_batch_get_one(&t, 1, llama_get_kv_cache_used_cells(g_ctx), 0)) != 0) {
                    LOGE("decode failed during generation");
                    break;
                }
            }

            llama_sampler_free(smpl);

            return env->NewStringUTF(out.c_str());
        }

        extern "C" JNIEXPORT void JNICALL
        Java_com_tomersch_mp3playerai_ai_LlamaLocalClient_nativeClose(
                JNIEnv* /*env*/, jobject /*thiz*/) {

            if (g_ctx) {
                llama_free(g_ctx);
                g_ctx = nullptr;
            }
            if (g_model) {
                llama_free_model(g_model);
                g_model = nullptr;
            }
            llama_backend_free();
            LOGI("Closed");
        }

    }
    llama_backend_free();
    LOGI("Closed");
}
