package com.tomersch.mp3playerai.ai.models;

import androidx.annotation.Nullable;

public class ModelInfo {
    public final String id;           // stable unique id (e.g. "gemma2b-it-int8")
    public final String displayName;  // shown in UI
    public final String url;          // direct download URL
    public final long expectedBytes;  // optional sanity check (0 if unknown)
    @Nullable public final String sha256; // optional integrity check

    public ModelInfo(String id, String displayName, String url, long expectedBytes, @Nullable String sha256) {
        this.id = id;
        this.displayName = displayName;
        this.url = url;
        this.expectedBytes = expectedBytes;
        this.sha256 = sha256;
    }

    @Override public String toString() {
        return displayName;
    }
}
