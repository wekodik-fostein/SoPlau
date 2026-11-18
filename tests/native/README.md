# Native bridge tests (host)

Checks the JNI bridge (`app/src/main/cpp/soplau_llama.cpp`) without an Android device.

1. Get the llama.cpp sources at the pinned tag (`b6000`) and its vocab file:
   `git clone --depth 1 --branch b6000 https://github.com/ggml-org/llama.cpp /tmp/llama`
   (the vocab file `models/ggml-vocab-llama-spm.gguf` is included in full clones; use `git clone` without `--filter`)
2. Build the bridge on the host (needs a JDK for `jni.h`):
   ```bash
   cmake -S app/src/main/cpp -B build-host \
     -DCMAKE_CXX_FLAGS="-I$JAVA_HOME/include -I$JAVA_HOME/include/linux"
   cmake --build build-host -j8 --target soplau_llama
   ```
3. Make a tiny random-weight test model (gibberish output, real tokenizer):
   ```bash
   GGUF_PY_DIR=/tmp/llama/gguf-py LLAMA_VOCAB_GGUF=/tmp/llama/models/ggml-vocab-llama-spm.gguf \
     python3 tests/native/make_tiny.py tiny.gguf
   ```
4. Run the JNI tests:
   ```bash
   javac -d out tests/native/com/soplau/rp/data/engine/*.java
   java -Djava.library.path=build-host -cp out com.soplau.rp.data.engine.Main tiny.gguf
   java -Djava.library.path=build-host -cp out com.soplau.rp.data.engine.Utf8Check tiny.gguf
   ```
