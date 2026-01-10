package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * ModelManager - Download and manage LLM models
 * 
 * Responsibilities:
 * - Download GGUF models from URLs
 * - Store models in internal storage
 * - Track available models
 * - Select active model
 * - Delete models to free space
 * 
 * Recommended models:
 * - TinyLlama-1.1B-Q4 (~600MB) - Fast, good for basic parsing
 * - Phi-2-Q4 (~1.6GB) - Better quality, still reasonable
 * - Mistral-7B-Q4 (~4GB) - High quality, slow on mobile
 */
public class ModelManager {
    private static final String TAG = "ModelManager";
    private static final String PREFS_NAME = "llm_models";
    private static final String PREF_ACTIVE_MODEL = "active_model";
    
    private Context context;
    private SharedPreferences prefs;
    private File modelsDir;

    public ModelConfig getModelByName(String name) {
        for (ModelConfig config : AVAILABLE_MODELS) {
            if (config.name.equals(name)) {
                return config;
            }
        }
        return null;
    }

    /**
     * Predefined model configurations
     */
    public static class ModelConfig {
        public String name;
        public String url;
        public long sizeBytes;
        public String description;
        
        public ModelConfig(String name, String url, long sizeBytes, String description) {
            this.name = name;
            this.url = url;
            this.sizeBytes = sizeBytes;
            this.description = description;
        }
    }
    
    // Available models (update these URLs with actual sources)
    public static final ModelConfig[] AVAILABLE_MODELS = {
        new ModelConfig(
            "tinyllama-1.1b-q4.gguf",
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            731000000,  // ~700MB
            "TinyLlama 1.1B - Fast, good for basic query parsing"
        ),
        new ModelConfig(
            "phi-2-q4.gguf",
            "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
            1700000000,  // ~1.6GB
            "Phi-2 - Better quality, reasonable speed"
        ),
        new ModelConfig(
            "mistral-7b-q4.gguf",
            "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            4370000000L,  // ~4GB
            "Mistral 7B - High quality, slow on mobile"
        )
    };
    
    public ModelManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Create models directory
        this.modelsDir = new File(context.getFilesDir(), "models");
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }
    }
    
    /**
     * Get list of available model configs
     */
    public List<ModelConfig> getAvailableModelConfigs() {
        List<ModelConfig> configs = new ArrayList<>();
        for (ModelConfig config : AVAILABLE_MODELS) {
            configs.add(config);
        }
        return configs;
    }
    
    /**
     * Get list of downloaded models
     */
    public List<String> getDownloadedModels() {
        List<String> models = new ArrayList<>();
        
        File[] files = modelsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".gguf")) {
                    models.add(file.getName());
                }
            }
        }
        
        return models;
    }
    
    /**
     * Check if a model is downloaded
     */
    public boolean isModelDownloaded(String modelName) {
        File modelFile = new File(modelsDir, modelName);
        return modelFile.exists();
    }
    
    /**
     * Get path to a downloaded model
     */
    public String getModelPath(String modelName) {
        File modelFile = new File(modelsDir, modelName);
        if (modelFile.exists()) {
            return modelFile.getPath();
        }
        return null;
    }
    
    /**
     * Get the currently active model
     */
    public String getActiveModel() {
        return prefs.getString(PREF_ACTIVE_MODEL, null);
    }
    
    /**
     * Set the active model
     */
    public void setActiveModel(String modelName) {
        if (isModelDownloaded(modelName)) {
            prefs.edit().putString(PREF_ACTIVE_MODEL, modelName).apply();
            Log.d(TAG, "Active model set to: " + modelName);
        } else {
            Log.e(TAG, "Cannot set active model, not downloaded: " + modelName);
        }
    }
    
    /**
     * Get path to the active model
     */
    public String getActiveModelPath() {
        String activeModel = getActiveModel();
        if (activeModel != null) {
            return getModelPath(activeModel);
        }
        return null;
    }
    
    /**
     * Download a model (blocking operation - run in background thread!)
     * 
     * @param modelConfig Model configuration to download
     * @param progressCallback Callback for download progress (0-100)
     * @return true if successful, false otherwise
     */
    public boolean downloadModel(ModelConfig modelConfig, ProgressCallback progressCallback) {
        Log.d(TAG, "Starting download: " + modelConfig.name);
        
        File outputFile = new File(modelsDir, modelConfig.name);
        
        // Skip if already exists
        if (outputFile.exists()) {
            Log.d(TAG, "Model already downloaded: " + modelConfig.name);
            if (progressCallback != null) {
                progressCallback.onProgress(100);
            }
            return true;
        }
        
        FileOutputStream outputStream = null;
        InputStream inputStream = null;
        
        try {
            // Setup connection
            URL url = new URL(modelConfig.url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed, HTTP code: " + responseCode);
                return false;
            }
            
            long fileSize = connection.getContentLength();
            Log.d(TAG, "Downloading " + (fileSize / (1024 * 1024)) + " MB");
            
            // Download file
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(outputFile);
            
            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
            int bytesRead;
            long totalBytesRead = 0;
            int lastProgress = -1;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                // Report progress
                if (progressCallback != null && fileSize > 0) {
                    int progress = (int) ((totalBytesRead * 100) / fileSize);
                    if (progress != lastProgress) {
                        progressCallback.onProgress(progress);
                        lastProgress = progress;
                    }
                }
                
                // Log progress every 100MB
                if (totalBytesRead % (100 * 1024 * 1024) == 0) {
                    Log.d(TAG, "Downloaded " + (totalBytesRead / (1024 * 1024)) + " MB");
                }
            }
            
            outputStream.flush();
            
            Log.d(TAG, "✅ Download complete: " + modelConfig.name);
            
            // Set as active model if it's the first one
            if (getActiveModel() == null) {
                setActiveModel(modelConfig.name);
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + e.getMessage());
            
            // Delete partial file
            if (outputFile.exists()) {
                outputFile.delete();
            }
            
            return false;
            
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Delete a model to free space
     * 
     * @param modelName Name of model to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteModel(String modelName) {
        File modelFile = new File(modelsDir, modelName);
        
        if (!modelFile.exists()) {
            Log.d(TAG, "Model doesn't exist: " + modelName);
            return false;
        }
        
        // Don't delete if it's the active model
        if (modelName.equals(getActiveModel())) {
            Log.e(TAG, "Cannot delete active model: " + modelName);
            return false;
        }
        
        boolean deleted = modelFile.delete();
        
        if (deleted) {
            Log.d(TAG, "✅ Model deleted: " + modelName);
        } else {
            Log.e(TAG, "Failed to delete model: " + modelName);
        }
        
        return deleted;
    }
    
    /**
     * Get total size of downloaded models
     */
    public long getTotalModelsSize() {
        long totalSize = 0;
        
        File[] files = modelsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".gguf")) {
                    totalSize += file.length();
                }
            }
        }
        
        return totalSize;
    }
    
    /**
     * Callback interface for download progress
     */
    public interface ProgressCallback {
        void onProgress(int percent);
    }
}
