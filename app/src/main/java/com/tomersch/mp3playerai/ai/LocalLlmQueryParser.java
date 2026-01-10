package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlinx.coroutines.Dispatchers;

/**
 * Reusable on-device LLM wrapper:
 * - Parse music queries into a stable "QueryProfile" vector
 * - Can also be used for playlist naming, summaries, etc.
 */
public final class LocalLlmQueryParser implements AutoCloseable {
    private static final String TAG = "LocalLlmQueryParser";

    private final Context appContext;
    private final LlmInference llm;

    public LocalLlmQueryParser(@NonNull Context context, String modelTaskPath) {
        this.appContext = context.getApplicationContext();
        LlmInference.LlmInferenceOptions  options = LlmInferenceOptions.builder()
                .setModelPath(modelTaskPath)
                .setMaxTokens(512)
                .setMaxTopK(64)

                .build();

        this.llm = LlmInference.createFromOptions(appContext, options);

        Log.d(TAG, "LLM initialized with model: " + modelTaskPath);
    }

    /**
     * Convert free-text query into a structured vector that the recommender can score against.
     */
    public QueryProfile parseToProfile(String userQuery) {
        if (userQuery == null) userQuery = "";

        // Force JSON-only output to make it deterministic to parse.
        String prompt =
                "You are a music assistant that converts a user request into JSON preferences.\n" +
                        "Return ONLY a single JSON object, no markdown, no extra text.\n" +
                        "Schema:\n" +
                        "{\n" +
                        "  \"moods\": {\"hype\":0-100,\"aggressive\":0-100,\"melodic\":0-100,\"atmospheric\":0-100,\"cinematic\":0-100,\"rhythmic\":0-100},\n" +
                        "  \"keywords\": [\"lowercase\",\"tokens\"],\n" +
                        "  \"topPercent\": 0.01-0.30,\n" +
                        "  \"temperature\": 0.10-1.50,\n" +
                        "  \"avoidRecentMinutes\": 5-240\n" +
                        "}\n" +
                        "User query: " + userQuery;

        try {
            String raw = llm.generateResponse(prompt);
            JSONObject obj = new JSONObject(raw);

            Map<String, Integer> moods = new HashMap<>();
            JSONObject moodsObj = obj.optJSONObject("moods");
            if (moodsObj != null) {
                moods.put("hype", clampInt(moodsObj.optInt("hype", 50), 0, 100));
                moods.put("aggressive", clampInt(moodsObj.optInt("aggressive", 50), 0, 100));
                moods.put("melodic", clampInt(moodsObj.optInt("melodic", 50), 0, 100));
                moods.put("atmospheric", clampInt(moodsObj.optInt("atmospheric", 50), 0, 100));
                moods.put("cinematic", clampInt(moodsObj.optInt("cinematic", 50), 0, 100));
                moods.put("rhythmic", clampInt(moodsObj.optInt("rhythmic", 50), 0, 100));
            } else {
                moods = QueryProfile.defaults().moods;
            }

            List<String> keywords = new ArrayList<>();
            JSONArray arr = obj.optJSONArray("keywords");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim().toLowerCase();
                    if (s.length() >= 2) keywords.add(s);
                }
            }

            float topPercent = clampFloat((float) obj.optDouble("topPercent", 0.05), 0.01f, 0.30f);
            float temperature = clampFloat((float) obj.optDouble("temperature", 0.55), 0.10f, 1.50f);
            int avoidRecentMinutes = clampInt(obj.optInt("avoidRecentMinutes", 30), 5, 240);

            return new QueryProfile(moods, keywords, topPercent, temperature, avoidRecentMinutes);

        } catch (Exception e) {
            Log.w(TAG, "LLM parse failed; falling back to defaults. Query=" + userQuery, e);
            // Fallback: keep your app working even if model missing/crashes
            QueryProfile fallback = QueryProfile.defaults();
            if (!userQuery.trim().isEmpty()) {
                // cheap keyword fallback
                for (String t : userQuery.toLowerCase().split("\\s+")) {
                    if (t.length() >= 3) fallback.keywords.add(t);
                }
            }
            return fallback;
        }
    }

    /**
     * Example extra use: name a playlist using the same LLM.
     */
    public String suggestPlaylistName(String userQuery, List<String> sampleTitles) {
        String prompt =
                "Suggest a short playlist name (2-6 words). Return ONLY the name.\n" +
                        "User query: " + userQuery + "\n" +
                        "Example songs: " + sampleTitles;

        try {
            String out = llm.generateResponse(prompt);
            return out.trim().replaceAll("[\\r\\n]+", " ");
        } catch (Exception e) {
            return "My Playlist";
        }
    }

    @Override
    public void close() {
        try {
            llm.close();
        } catch (Exception ignored) {}
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampFloat(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
