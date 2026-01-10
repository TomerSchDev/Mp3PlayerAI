package com.tomersch.mp3playerai.ai.models;


import android.content.Context;
import android.content.SharedPreferences;

public final class ModelPrefs {

    private static final String PREFS = "model_prefs";
    private static final String KEY_ACTIVE_MODEL_PATH = "active_model_path";

    private ModelPrefs() {}

    public static void setActiveModelPath(Context ctx, String pathOrNull) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ACTIVE_MODEL_PATH, pathOrNull).apply();
    }

    public static String getActiveModelPath(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_ACTIVE_MODEL_PATH, null);
    }
}
