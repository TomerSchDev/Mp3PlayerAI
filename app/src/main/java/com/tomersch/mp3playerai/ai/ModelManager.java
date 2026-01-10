package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.tomersch.mp3playerai.ai.models.ModelInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelManager {
    private static final String PREFS = "model_manager_prefs";
    private static final String KEY_SELECTED_MODEL_ID = "selected_model_id";

    private final Context appContext;
    private final SharedPreferences prefs;

    public ModelManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // 1) Where models live
    public File getModelsDir() {
        File dir = new File(appContext.getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // 2) Your remote catalog (edit these)
    public List<ModelInfo> getRemoteCatalog() {
        // Put direct download URLs here.
        // For HuggingFace, prefer "resolve/main/..." direct file links.
        return Arrays.asList(
                new ModelInfo(
                        "gemma2b-it-int8",
                        "Gemma 2B (instruction, int8)",
                        "https://YOUR_HOSTING/gemma2b_it_int8.task",
                        0L,
                        null
                ),
                new ModelInfo(
                        "phi-mini-int8",
                        "Phi Mini (int8)",
                        "https://YOUR_HOSTING/phi_mini_int8.task",
                        0L,
                        null
                )
        );
    }

    // 3) Local inventory
    public List<File> listInstalledModelFiles() {
        File[] files = getModelsDir().listFiles();
        List<File> out = new ArrayList<>();
        if (files == null) return out;

        for (File f : files) {
            if (f.isFile() && !f.getName().endsWith(".partial")) {
                out.add(f);
            }
        }
        return out;
    }

    @Nullable
    public File getInstalledModelFileById(String modelId) {
        File f = new File(getModelsDir(), modelId + ".task");
        return f.exists() ? f : null;
    }

    public boolean isInstalled(String modelId) {
        return getInstalledModelFileById(modelId) != null;
    }

    // 4) Selection
    public void setSelectedModelId(@Nullable String modelId) {
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply();
    }

    @Nullable
    public String getSelectedModelId() {
        return prefs.getString(KEY_SELECTED_MODEL_ID, null);
    }

    @Nullable
    public File getSelectedModelFile() {
        String id = getSelectedModelId();
        if (id == null) return null;
        return getInstalledModelFileById(id);
    }

    // 5) Delete
    public boolean deleteModel(String modelId) {
        File f = new File(getModelsDir(), modelId + ".task");
        boolean ok = !f.exists() || f.delete();

        // Also delete partial if exists
        File p = new File(getModelsDir(), modelId + ".task.partial");
        if (p.exists()) p.delete();

        // If deleted was selected, clear selection
        String selected = getSelectedModelId();
        if (selected != null && selected.equals(modelId)) {
            setSelectedModelId(null);
        }
        return ok;
    }

    // 6) Download (WorkManager)
    public String enqueueDownload(@NonNull ModelInfo info) {
        String modelId = info.id;

        File target = new File(getModelsDir(), modelId + ".task");

        Data input = new Data.Builder()
                .putString(ModelDownloadWorker.KEY_DISPLAY_NAME, modelId)
                .putString(ModelDownloadWorker.KEY_FILE_NAME, info.url)
                .putString(ModelDownloadWorker.KEY_URL, target.getAbsolutePath())
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ModelDownloadWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .build();

        // Use unique name per model so repeat taps don't schedule duplicates
        String uniqueWorkName = "download_model_" + modelId;

        WorkManager.getInstance(appContext)
                .enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.KEEP, req);

        return uniqueWorkName;
    }
}
