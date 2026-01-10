package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Local LLM Interpreter using llama.cpp
 * 
 * Purpose: Parse natural language music queries into structured QueryProfile objects.
 * Examples:
 *   "anime openings" -> QueryProfile{genres=[anime, jrock], moods={hype:80, melodic:70}}
 *   "dark cinematic focus music" -> QueryProfile{genres=[soundtrack], moods={cinematic:90, atmospheric:80}}
 *   "late night chill electronic" -> QueryProfile{genres=[electronic], moods={atmospheric:80, hype:20}}
 * 
 * This is a reusable wrapper that can be used for:
 *   - Query parsing (current use)
 *   - Playlist naming (future)
 *   - Mood inference (future)
 *   - Any other LLM tasks
 */
public class LocalLlmInterpreter {
    private static final String TAG = "LocalLlmInterpreter";
    
    private Context context;
    private long llamaContextPtr = 0;
    private String modelPath;
    private boolean isInitialized = false;
    
    // Load native library
    static {
        try {
            System.loadLibrary("llama-android");
            Log.d(TAG, "✅ Native library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "❌ Failed to load native library: " + e.getMessage());
        }
    }
    
    /**
     * Initialize with a specific model
     * 
     * @param context Android context
     * @param modelPath Path to GGUF model file
     */
    public LocalLlmInterpreter(Context context, String modelPath) {
        this.context = context;
        this.modelPath = modelPath;
    }
    
    /**
     * Initialize the LLM (loads model into memory)
     * This is expensive (3-4GB RAM), so only call when needed
     */
    public boolean initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized");
            return true;
        }
        
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: " + modelPath);
            return false;
        }
        
        Log.d(TAG, "Initializing LLM with model: " + modelPath);
        
        // Initialize llama.cpp
        // Parameters:
        //   nCtx: 2048 (smaller context for faster inference)
        //   nThreads: 4 (use multiple CPU cores)
        llamaContextPtr = initLlama(modelPath, 2048, 4);
        
        if (llamaContextPtr == 0) {
            Log.e(TAG, "Failed to initialize Llama");
            return false;
        }
        
        isInitialized = true;
        Log.d(TAG, "✅ LLM initialized successfully");
        return true;
    }
    
    /**
     * Parse a natural language music query into a QueryProfile
     * 
     * @param query Natural language query (e.g., "upbeat anime openings")
     * @return QueryProfile with extracted genres and moods
     */
    public QueryProfile parseQuery(String query) {
        if (!isInitialized) {
            Log.e(TAG, "LLM not initialized, returning default profile");
            return new QueryProfile();
        }
        
        // Build prompt for query parsing
        String prompt = buildQueryParsePrompt(query);
        
        Log.d(TAG, "Parsing query: " + query);
        
        // Generate response from LLM
        // Parameters:
        //   temperature: 0.3 (low for more deterministic output)
        //   maxTokens: 256 (enough for JSON response)
        String response = generateText(llamaContextPtr, prompt, 0.3f, 256);
        
        if (response == null || response.isEmpty()) {
            Log.e(TAG, "Empty response from LLM");
            return new QueryProfile();
        }
        
        Log.d(TAG, "LLM response: " + response);
        
        // Parse JSON response into QueryProfile
        QueryProfile profile = QueryProfile.fromLlmJson(response);
        
        Log.d(TAG, "Parsed profile: " + profile);
        
        return profile;
    }
    
    /**
     * Build prompt for parsing music queries
     * This is the key to good LLM performance!
     */
    private String buildQueryParsePrompt(String query) {
        return "You are a music recommendation expert. Parse this music query into JSON format.\n\n" +
               "Query: \"" + query + "\"\n\n" +
               "Extract:\n" +
               "1. genres: array of genre strings (e.g., [\"rock\", \"metal\", \"anime\"])\n" +
               "2. moods: object with mood scores 0-100:\n" +
               "   - hype: energetic, upbeat, party vibes (0=calm, 100=intense)\n" +
               "   - aggressive: hard, harsh, powerful (0=gentle, 100=brutal)\n" +
               "   - melodic: tuneful, harmonic, vocal focus (0=atonal, 100=very melodic)\n" +
               "   - atmospheric: ambient, spacey, immersive (0=dry, 100=ethereal)\n" +
               "   - cinematic: epic, orchestral, dramatic (0=simple, 100=grand)\n" +
               "   - rhythmic: beat-driven, danceable (0=free-form, 100=strict rhythm)\n\n" +
               "Output ONLY valid JSON, no explanation:\n" +
               "{\"genres\":[...],\"moods\":{\"hype\":N,\"aggressive\":N,\"melodic\":N,\"atmospheric\":N,\"cinematic\":N,\"rhythmic\":N}}\n\n" +
               "JSON:";
    }
    
    /**
     * Free LLM resources (releases 3-4GB RAM!)
     * Always call this when done to avoid memory issues
     */
    public void close() {
        if (isInitialized && llamaContextPtr != 0) {
            freeLlama(llamaContextPtr);
            llamaContextPtr = 0;
            isInitialized = false;
            Log.d(TAG, "✅ LLM resources freed");
        }
    }
    
    /**
     * Check if LLM is ready to use
     */
    public boolean isReady() {
        return isInitialized && llamaContextPtr != 0;
    }
    
    // =========================
    // JNI Native Methods
    // =========================
    
    /**
     * Initialize llama.cpp model
     * 
     * @param modelPath Path to GGUF model file
     * @param nCtx Context size (number of tokens)
     * @param nThreads Number of CPU threads
     * @return Context pointer (0 if failed)
     */
    private native long initLlama(String modelPath, int nCtx, int nThreads);
    
    /**
     * Generate text from prompt
     * 
     * @param contextPtr Llama context pointer
     * @param prompt Input prompt
     * @param temperature Sampling temperature (0.0-2.0)
     * @param maxTokens Maximum tokens to generate
     * @return Generated text
     */
    private native String generateText(long contextPtr, String prompt, float temperature, int maxTokens);
    
    /**
     * Free llama.cpp context
     * 
     * @param contextPtr Llama context pointer
     */
    private native void freeLlama(long contextPtr);
    
    /**
     * Static helper: Copy model from assets to internal storage
     * Only needs to be done once per model
     * 
     * @param context Android context
     * @param assetModelName Model filename in assets (e.g., "tinyllama-1.1b-q4.gguf")
     * @return Path to copied model, or null if failed
     */
    public static String copyModelFromAssets(Context context, String assetModelName) {
        try {
            File modelsDir = new File(context.getFilesDir(), "models");
            if (!modelsDir.exists()) {
                modelsDir.mkdirs();
            }
            
            File modelFile = new File(modelsDir, assetModelName);
            
            // Skip if already exists
            if (modelFile.exists()) {
                Log.d(TAG, "Model already exists: " + modelFile.getPath());
                return modelFile.getPath();
            }
            
            Log.d(TAG, "Copying model from assets: " + assetModelName);
            
            InputStream inputStream = context.getAssets().open(assetModelName);
            FileOutputStream outputStream = new FileOutputStream(modelFile);
            
            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
            int length;
            long totalBytes = 0;
            
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
                totalBytes += length;
                
                // Log progress every 100MB
                if (totalBytes % (100 * 1024 * 1024) == 0) {
                    Log.d(TAG, "Copied " + (totalBytes / (1024 * 1024)) + " MB");
                }
            }
            
            outputStream.flush();
            outputStream.close();
            inputStream.close();
            
            Log.d(TAG, "✅ Model copied successfully: " + modelFile.getPath());
            return modelFile.getPath();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy model from assets: " + e.getMessage());
            return null;
        }
    }
}
