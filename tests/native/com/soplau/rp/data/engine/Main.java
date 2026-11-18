package com.soplau.rp.data.engine;

public class Main {
    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        System.loadLibrary("soplau_llama");
        String model = args[0];
        LlamaNative n = new LlamaNative();

        // 1. Missing file -> handle 0.
        check("load missing file returns 0", n.nativeLoad("/nope/missing.gguf", 512, 2) == 0);

        // 2. Load the tiny model.
        long h = n.nativeLoad(model, 512, 2);
        check("load tiny model returns handle", h != 0);
        if (h == 0) System.exit(1);

        String[] roles = {"system", "user"};
        String[] contents = {"Ты играешь роль Алисы.", "Привет, Алиса!"};

        // 3. Greedy generation streams tokens and finishes with code 0.
        StringBuilder out = new StringBuilder();
        int[] calls = {0};
        int code = n.nativeGenerate(h, roles, contents, 0.0f, 32, token -> {
            calls[0]++;
            out.append(token);
        });
        check("greedy generate returns 0 (got " + code + ")", code == 0);
        check("greedy generate streamed at least one chunk", calls[0] > 0);
        System.out.println("  greedy output (" + calls[0] + " chunks): " + out);

        // 4. Greedy is deterministic: second run gives the same text.
        StringBuilder out2 = new StringBuilder();
        n.nativeGenerate(h, roles, contents, 0.0f, 32, t -> out2.append(t));
        check("greedy output is deterministic", out.toString().equals(out2.toString()));

        // 5. Sampling with temperature also works.
        StringBuilder out3 = new StringBuilder();
        int code3 = n.nativeGenerate(h, roles, contents, 0.8f, 32, t -> out3.append(t));
        check("sampled generate returns 0 (got " + code3 + ")", code3 == 0);

        // 6. Prompt longer than context -> -2 (prompt too long).
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 2000; i++) big.append("слово ");
        int codeLong = n.nativeGenerate(h, new String[]{"user"}, new String[]{big.toString()}, 0.5f, 16, t -> {});
        check("oversized prompt returns -2 (got " + codeLong + ")", codeLong == -2);

        // 7. Sink that throws (user pressed stop) must abort with the exception, not crash.
        boolean thrown = false;
        try {
            n.nativeGenerate(h, roles, contents, 0.0f, 64, t -> { throw new IllegalStateException("stop"); });
        } catch (IllegalStateException e) {
            thrown = true;
        }
        check("throwing sink aborts generation with the exception", thrown);

        // 8. After the abort the handle still works.
        StringBuilder out4 = new StringBuilder();
        int code4 = n.nativeGenerate(h, roles, contents, 0.0f, 8, t -> out4.append(t));
        check("handle still usable after abort (code " + code4 + ")", code4 == 0);

        n.nativeFree(h);
        check("free does not crash", true);

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
