package com.soplau.rp.data.engine;

// Mirrors `fun interface TokenSink` from Kotlin.
public interface TokenSink {
    void onToken(String token);
}
