package com.tomersch.mp3playerai.ai.models;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ModelStorage {

    private static final String MODELS_DIR_NAME = "models";

    private ModelStorage() {}

    public static File getModelsDir(Context ctx) {
        return new File(ctx.getFilesDir(), MODELS_DIR_NAME);
    }

    public static void ensureModelsDir(Context ctx) {
        File dir = getModelsDir(ctx);
        if (!dir.exists()) dir.mkdirs();
    }

    public static File getModelFile(Context ctx, String fileName) {
        return new File(getModelsDir(ctx), fileName);
    }

    public static List<File> listInstalledModels(Context ctx) {
        ensureModelsDir(ctx);
        File dir = getModelsDir(ctx);

        File[] files = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (files == null) return out;

        for (File f : files) {
            if (f.isFile() && f.length() > 0) {
                out.add(f);
            }
        }
        return out;
    }
}
