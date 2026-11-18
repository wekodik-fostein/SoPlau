// JNI bridge between GgufEngine (Kotlin) and llama.cpp.
//
// Kotlin side: com.soplau.rp.data.engine.GgufEngine
//   external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
//   external fun nativeFree(handle: Long)
//   external fun nativeGenerate(handle: Long, roles: Array<String>, contents: Array<String>,
//                               temperature: Float, maxTokens: Int, sink: TokenSink): Int
//   TokenSink.onToken(text: String) is called for every decoded chunk of text.
//
// The chat prompt is formatted with the model's own chat template (GGUF metadata),
// so no hand-written template is needed for each model family.

#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <random>
#include <string>
#include <vector>

#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define SOPLAU_TAG "SoPlauLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SOPLAU_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, SOPLAU_TAG, __VA_ARGS__)
#else
#define LOGE(...) std::fprintf(stderr, __VA_ARGS__)
#define LOGI(...) std::fprintf(stderr, __VA_ARGS__)
#endif

namespace {

// Error codes returned to Kotlin (nativeGenerate). 0 means success.
constexpr jint kOk = 0;
constexpr jint kErrPromptTooLong = -2;
constexpr jint kErrTokenize = -3;
constexpr jint kErrDecode = -4;
constexpr jint kErrCallback = -5;

struct Handle {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int32_t n_ctx = 0;
};

std::string to_std(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

// Java strings use modified UTF-8, which cannot carry 4-byte characters (most emoji).
// Build the jstring from raw UTF-8 bytes via new String(bytes, "UTF-8") instead.
jstring utf8_to_jstring(JNIEnv * env, const std::string & bytes) {
    jclass string_cls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(string_cls, "<init>", "([BLjava/lang/String;)V");
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()),
                            reinterpret_cast<const jbyte *>(bytes.data()));
    jstring charset = env->NewStringUTF("UTF-8");
    jstring out = static_cast<jstring>(env->NewObject(string_cls, ctor, arr, charset));
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(string_cls);
    return out;
}

// Number of trailing bytes that form an unfinished UTF-8 sequence.
// Those bytes are held back until the next token completes them.
size_t incomplete_utf8_tail(const std::string & s) {
    const size_t n = s.size();
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        const unsigned char c = static_cast<unsigned char>(s[n - back]);
        if ((c & 0xC0) == 0x80) continue;  // continuation byte, keep looking for the lead
        size_t need;
        if ((c & 0x80) == 0x00)      need = 1;
        else if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        else return 0;
        return need > back ? back : 0;
    }
    return 0;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text,
                                  bool add_special) {
    const int32_t needed = -llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                           nullptr, 0, add_special, /*parse_special=*/true);
    if (needed <= 0) return {};
    std::vector<llama_token> tokens(needed);
    const int32_t got = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                       tokens.data(), needed, add_special, /*parse_special=*/true);
    if (got < 0) return {};
    tokens.resize(got);
    return tokens;
}

// Formats messages with the model's chat template. Falls back to ChatML when the model has none.
std::string format_prompt(const llama_model * model, const std::vector<std::string> & roles,
                          const std::vector<std::string> & contents) {
    std::vector<llama_chat_message> msgs;
    msgs.reserve(roles.size());
    for (size_t i = 0; i < roles.size(); ++i) {
        msgs.push_back({roles[i].c_str(), contents[i].c_str()});
    }

    // Model-provided template (nullptr = default from GGUF metadata).
    int32_t size = llama_chat_apply_template(nullptr, msgs.data(), msgs.size(), true, nullptr, 0);
    if (size > 0) {
        std::string out(static_cast<size_t>(size), '\0');
        const int32_t written = llama_chat_apply_template(nullptr, msgs.data(), msgs.size(), true,
                                                          out.data(), size);
        if (written > 0) {
            out.resize(static_cast<size_t>(written));
            return out;
        }
    }

    LOGI("model has no usable chat template, using ChatML fallback");
    std::string out;
    for (size_t i = 0; i < roles.size(); ++i) {
        out += "<|im_start|>" + roles[i] + "\n" + contents[i] + "<|im_end|>\n";
    }
    out += "<|im_start|>assistant\n";
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_soplau_rp_data_engine_LlamaNative_nativeLoad(JNIEnv * env, jobject /*thiz*/,
                                                     jstring jpath, jint n_ctx, jint n_threads) {
    llama_backend_init();

    const std::string path = to_std(env, jpath);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // CPU only on Android
    llama_model * model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("failed to load model: %s", path.c_str());
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = static_cast<uint32_t>(n_ctx);  // whole prompt in one decode call
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;

    llama_context * ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("failed to create context (n_ctx=%d)", static_cast<int>(n_ctx));
        llama_model_free(model);
        return 0;
    }

    auto * handle = new Handle();
    handle->model = model;
    handle->ctx = ctx;
    handle->vocab = llama_model_get_vocab(model);
    handle->n_ctx = static_cast<int32_t>(llama_n_ctx(ctx));
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_soplau_rp_data_engine_LlamaNative_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/,
                                                     jlong handle_ptr) {
    auto * handle = reinterpret_cast<Handle *>(handle_ptr);
    if (handle == nullptr) return;
    llama_free(handle->ctx);
    llama_model_free(handle->model);
    delete handle;
}

JNIEXPORT jint JNICALL
Java_com_soplau_rp_data_engine_LlamaNative_nativeGenerate(JNIEnv * env, jobject /*thiz*/,
                                                         jlong handle_ptr, jobjectArray jroles,
                                                         jobjectArray jcontents, jfloat temperature,
                                                         jint max_tokens, jobject sink) {
    auto * h = reinterpret_cast<Handle *>(handle_ptr);
    if (h == nullptr) return kErrTokenize;

    // 1. Read messages from Java.
    const jsize n_msgs = env->GetArrayLength(jroles);
    std::vector<std::string> roles;
    std::vector<std::string> contents;
    roles.reserve(n_msgs);
    contents.reserve(n_msgs);
    for (jsize i = 0; i < n_msgs; ++i) {
        auto jr = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
        auto jc = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
        roles.push_back(to_std(env, jr));
        contents.push_back(to_std(env, jc));
        env->DeleteLocalRef(jr);
        env->DeleteLocalRef(jc);
    }

    // 2. Callback target.
    jclass sink_cls = env->GetObjectClass(sink);
    jmethodID on_token = env->GetMethodID(sink_cls, "onToken", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(sink_cls);
    if (on_token == nullptr) return kErrCallback;

    // 3. Prompt -> tokens.
    const std::string prompt = format_prompt(h->model, roles, contents);

    // Add BOS only if the model wants it AND the template did not already write it,
    // otherwise the model sees two BOS tokens.
    bool add_bos = false;
    if (llama_vocab_get_add_bos(h->vocab)) {
        char bos_buf[64];
        const int32_t bn = llama_token_to_piece(h->vocab, llama_vocab_bos(h->vocab), bos_buf,
                                                sizeof(bos_buf), 0, true);
        const std::string bos_text = bn > 0 ? std::string(bos_buf, static_cast<size_t>(bn)) : "";
        add_bos = bos_text.empty() || prompt.compare(0, bos_text.size(), bos_text) != 0;
    }
    std::vector<llama_token> tokens = tokenize(h->vocab, prompt, add_bos);
    if (tokens.empty()) return kErrTokenize;

    const int32_t n_prompt = static_cast<int32_t>(tokens.size());
    if (n_prompt >= h->n_ctx - 16) return kErrPromptTooLong;
    const int32_t n_predict = std::min(static_cast<int32_t>(max_tokens), h->n_ctx - n_prompt);

    // 4. Fresh KV cache: every request resends the full history.
    llama_memory_clear(llama_get_memory(h->ctx), true);

    // 5. Sampler chain.
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        std::random_device rd;
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(rd()));
    }

    // 6. Decode the prompt.
    llama_batch batch = llama_batch_init(n_prompt, 0, 1);
    batch.n_tokens = n_prompt;
    for (int32_t i = 0; i < n_prompt; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_prompt - 1) ? 1 : 0;
    }
    jint result = kOk;
    if (llama_decode(h->ctx, batch) != 0) {
        result = kErrDecode;
    }
    llama_batch_free(batch);

    // 7. Generate token by token, streaming complete UTF-8 chunks to Java.
    llama_batch step = llama_batch_init(1, 0, 1);
    std::string pending;
    int32_t pos = n_prompt;

    auto emit = [&](const std::string & bytes) -> bool {
        jstring js = utf8_to_jstring(env, bytes);
        env->CallVoidMethod(sink, on_token, js);
        env->DeleteLocalRef(js);
        return !env->ExceptionCheck();
    };

    for (int32_t i = 0; result == kOk && i < n_predict; ++i) {
        const llama_token id = llama_sampler_sample(smpl, h->ctx, -1);
        llama_sampler_accept(smpl, id);
        if (llama_vocab_is_eog(h->vocab, id)) break;

        char piece[256];
        int32_t n = llama_token_to_piece(h->vocab, id, piece, sizeof(piece), 0, false);
        std::string text;
        if (n < 0) {
            std::vector<char> big(static_cast<size_t>(-n));
            n = llama_token_to_piece(h->vocab, id, big.data(), static_cast<int32_t>(big.size()), 0,
                                     false);
            if (n > 0) text.assign(big.data(), static_cast<size_t>(n));
        } else if (n > 0) {
            text.assign(piece, static_cast<size_t>(n));
        }

        pending += text;
        const size_t hold = incomplete_utf8_tail(pending);
        const size_t ready = pending.size() - hold;
        if (ready > 0) {
            if (!emit(pending.substr(0, ready))) {
                result = kErrCallback;
                break;
            }
            pending.erase(0, ready);
        }

        step.n_tokens = 1;
        step.token[0] = id;
        step.pos[0] = pos++;
        step.n_seq_id[0] = 1;
        step.seq_id[0][0] = 0;
        step.logits[0] = 1;
        if (llama_decode(h->ctx, step) != 0) {
            result = kErrDecode;
            break;
        }
    }

    if (result == kOk && !pending.empty()) {
        if (!emit(pending)) result = kErrCallback;
    }

    llama_batch_free(step);
    llama_sampler_free(smpl);
    return result;
}

}  // extern "C"
