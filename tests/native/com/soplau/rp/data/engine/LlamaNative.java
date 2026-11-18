package com.soplau.rp.data.engine;

// Mirrors `internal object LlamaNative` from Kotlin: instance native methods.
public class LlamaNative {
    public native long nativeLoad(String path, int nCtx, int nThreads);
    public native void nativeFree(long handle);
    public native int nativeGenerate(long handle, String[] roles, String[] contents,
                                     float temperature, int maxTokens, TokenSink sink);
}
