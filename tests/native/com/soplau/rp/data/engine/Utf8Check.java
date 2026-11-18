package com.soplau.rp.data.engine;

public class Utf8Check {
    public static void main(String[] args) {
        System.loadLibrary("soplau_llama");
        LlamaNative n = new LlamaNative();
        long h = n.nativeLoad(args[0], 512, 2);
        int totalReplacement = 0;
        int totalChars = 0;
        // Many samples at high temperature exercise multi-byte / byte-fallback tokens.
        for (int run = 0; run < 20; run++) {
            StringBuilder out = new StringBuilder();
            int code = n.nativeGenerate(h, new String[]{"user"}, new String[]{"Расскажи историю"},
                    1.2f, 120, t -> out.append(t));
            if (code != 0) { System.out.println("code " + code); System.exit(1); }
            String s = out.toString();
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '�') totalReplacement++;
            }
            totalChars += s.length();
        }
        n.nativeFree(h);
        System.out.println("chars=" + totalChars + " replacement_chars=" + totalReplacement);
        System.out.println(totalReplacement == 0 ? "UTF8 PASS" : "UTF8 FAIL");
    }
}
