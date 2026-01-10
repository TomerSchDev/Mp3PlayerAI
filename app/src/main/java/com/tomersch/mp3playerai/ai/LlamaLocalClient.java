package com.tomersch.mp3playerai.ai;

public class LlamaLocalClient {
    static {
        System.loadLibrary("llm_jni");
    }

    public boolean init(String modelPath) {
        return nativeInit(modelPath);
    }

    public String infer(String prompt) {
        return nativeInfer(prompt);
    }

    public void close() {
        nativeClose();
    }

    private native boolean nativeInit(String modelPath);
    private native String nativeInfer(String prompt);
    private native void nativeClose();
}
