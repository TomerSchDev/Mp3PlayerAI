package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.tomersch.mp3playerai.models.Song;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks user behavior and learns preferences to improve AI recommendations
 */
public class AILearningManager {
    private static final String TAG = "AILearningManager";
    private static final String PREFS_NAME = "ai_learning_prefs";

    // Preference keys
    private static final String KEY_PLAY_HISTORY = "play_history";
    private static final String KEY_SKIP_HISTORY = "skip_history";
    private static final String KEY_SONG_SCORES = "song_scores";
    private static final String KEY_MOOD_PREFERENCES = "mood_preferences";

    // Learning parameters
    private static final int MAX_HISTORY_SIZE = 500;
    private static final float PLAY_BOOST = 0.15f;
    private static final float SKIP_PENALTY = -0.25f;
    private static final float COMPLETE_BOOST = 0.30f;
    private static final float REPLAY_BOOST = 0.20f;

    private Context context;
    private SharedPreferences prefs;

    // In-memory cache
    private Map<String, Float> songScores;
    private Map<String, Float> moodPreferences;
    private List<String> playHistory;
    private List<String> skipHistory;
    private Map<String, Long> lastPlayedAt; // NEW

    public AILearningManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        loadLearningData();
    }

    /**
     * Load learning data from persistent storage
     */
    private void loadLearningData() {
        songScores = new HashMap<>();
        moodPreferences = new HashMap<>();
        playHistory = new ArrayList<>();
        skipHistory = new ArrayList<>();
        lastPlayedAt = new HashMap<>();
        String lastPlayedJson = prefs.getString(DBColumns.LAST_TIME_PLAYED.getName(), "{}");
        JSONObject lastPlayedObj = null;
        try {
            lastPlayedObj = new JSONObject(lastPlayedJson);
            for (java.util.Iterator<String> it = lastPlayedObj.keys(); it.hasNext(); ) {
                String key = it.next();
                lastPlayedAt.put(key, lastPlayedObj.getLong(key));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing lastPlayedAt JSON", e);
        }


        try {
            // Load song scores
            String scoresJson = prefs.getString(KEY_SONG_SCORES, "{}");
            JSONObject scoresObj = new JSONObject(scoresJson);
            for (java.util.Iterator<String> it = scoresObj.keys(); it.hasNext(); ) {
                String key = it.next();
                songScores.put(key, (float) scoresObj.getDouble(key));
            }

            // Load mood preferences
            String moodJson = prefs.getString(KEY_MOOD_PREFERENCES, "{}");
            JSONObject moodObj = new JSONObject(moodJson);
            for (java.util.Iterator<String> it = moodObj.keys(); it.hasNext(); ) {
                String key = it.next();
                moodPreferences.put(key, (float) moodObj.getDouble(key));
            }

            // Load play history
            String playJson = prefs.getString(KEY_PLAY_HISTORY, "[]");
            JSONArray playArray = new JSONArray(playJson);
            for (int i = 0; i < playArray.length(); i++) {
                playHistory.add(playArray.getString(i));
            }

            // Load skip history
            String skipJson = prefs.getString(KEY_SKIP_HISTORY, "[]");
            JSONArray skipArray = new JSONArray(skipJson);
            for (int i = 0; i < skipArray.length(); i++) {
                skipHistory.add(skipArray.getString(i));
            }

            Log.d(TAG, "Loaded learning data: " + songScores.size() + " song scores, " +
                    playHistory.size() + " play history items");

        } catch (Exception e) {
            Log.e(TAG, "Error loading learning data", e);
        }
    }
    public long getLastPlayedAt(String path) {
        if (path == null) return 0L;
        Long ts = lastPlayedAt.get(path);
        return ts != null ? ts : 0L;
    }


    /**
     * Save learning data to persistent storage
     */
    private void saveLearningData() {
        try {
            SharedPreferences.Editor editor = prefs.edit();

            // Save song scores
            JSONObject scoresObj = new JSONObject();
            for (Map.Entry<String, Float> entry : songScores.entrySet()) {
                scoresObj.put(entry.getKey(), entry.getValue());
            }
            editor.putString(KEY_SONG_SCORES, scoresObj.toString());

            // Save mood preferences
            JSONObject moodObj = new JSONObject();
            for (Map.Entry<String, Float> entry : moodPreferences.entrySet()) {
                moodObj.put(entry.getKey(), entry.getValue());
            }
            editor.putString(KEY_MOOD_PREFERENCES, moodObj.toString());

            // Save play history (last MAX_HISTORY_SIZE items)
            JSONArray playArray = new JSONArray();
            int playStart = Math.max(0, playHistory.size() - MAX_HISTORY_SIZE);
            for (int i = playStart; i < playHistory.size(); i++) {
                playArray.put(playHistory.get(i));
            }
            editor.putString(KEY_PLAY_HISTORY, playArray.toString());
            JSONObject lastPlayedObj = new JSONObject();
            for (Map.Entry<String, Long> entry : lastPlayedAt.entrySet()) {
                lastPlayedObj.put(entry.getKey(), entry.getValue());
            }
            editor.putString(DBColumns.LAST_TIME_PLAYED.getName(), lastPlayedObj.toString());
            // Save skip history (last MAX_HISTORY_SIZE items)
            JSONArray skipArray = new JSONArray();
            int skipStart = Math.max(0, skipHistory.size() - MAX_HISTORY_SIZE);
            for (int i = skipStart; i < skipHistory.size(); i++) {
                skipArray.put(skipHistory.get(i));
            }
            editor.putString(KEY_SKIP_HISTORY, skipArray.toString());

            editor.apply();

        } catch (Exception e) {
            Log.e(TAG, "Error saving learning data", e);
        }
    }

    /**
     * Record that a song was played
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void recordSongPlayed(String songPath) {
        if (songPath == null) return;

        playHistory.add(songPath);

        // NEW: update last played timestamp
        lastPlayedAt.put(songPath, System.currentTimeMillis());

        // Boost song score
        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.min(currentScore + PLAY_BOOST, 1.0f));

        // Remove from skip history if it was there
        skipHistory.remove(songPath);

        saveLearningData();
        Log.d(TAG, "Song played: " + songPath + ", new score: " + songScores.get(songPath));
    }

    /**
     * Record that a song was skipped
     */
    public void recordSongSkipped(String songPath) {
        skipHistory.add(songPath);

        // Penalize song score
        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.max(currentScore + SKIP_PENALTY, -1.0f));

        saveLearningData();
        Log.d(TAG, "Song skipped: " + songPath + ", new score: " + songScores.get(songPath));
    }

    /**
     * Record that a song was played to completion
     */
    public void recordSongCompleted(String songPath) {
        // Extra boost for completing a song
        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.min(currentScore + COMPLETE_BOOST, 1.0f));

        saveLearningData();
        Log.d(TAG, "Song completed: " + songPath + ", new score: " + songScores.get(songPath));
    }

    /**
     * Record that a song was replayed
     */
    public void recordSongReplayed(String songPath) {
        // User loved it enough to replay!
        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.min(currentScore + REPLAY_BOOST, 1.0f));

        saveLearningData();
        Log.d(TAG, "Song replayed: " + songPath + ", new score: " + songScores.get(songPath));
    }

    /**
     * Learn from mood preferences when user generates playlists
     */
    public void learnMoodPreferences(Map<String, Integer> moodPrefs, boolean userLiked) {
        float adjustment = userLiked ? 0.05f : -0.05f;

        for (Map.Entry<String, Integer> entry : moodPrefs.entrySet()) {
            String mood = entry.getKey();
            float currentPref = moodPreferences.getOrDefault(mood, 0.5f);

            // Adjust preference based on slider value and whether user liked it
            float targetValue = entry.getValue() / 100.0f;
            float newPref = currentPref + (targetValue - currentPref) * adjustment;
            moodPreferences.put(mood, Math.max(0.0f, Math.min(1.0f, newPref)));
        }

        saveLearningData();
    }

    /**
     * Get learned score for a song (-1.0 to 1.0)
     */
    public float getSongScore(String songPath) {
        return songScores.getOrDefault(songPath, 0.0f);
    }

    /**
     * Get all song scores
     */
    public Map<String, Float> getAllSongScores() {
        return new HashMap<>(songScores);
    }

    /**
     * Get learned mood preferences
     */
    public Map<String, Float> getMoodPreferences() {
        return new HashMap<>(moodPreferences);
    }

    /**
     * Get frequently played songs (top N)
     */
    public List<String> getFrequentlyPlayed(int count) {
        Map<String, Integer> playCounts = new HashMap<>();

        for (String path : playHistory) {
            playCounts.put(path, playCounts.getOrDefault(path, 0) + 1);
        }

        List<String> result = new ArrayList<>(playCounts.keySet());
        result.sort((a, b) -> playCounts.get(b) - playCounts.get(a));

        return result.subList(0, Math.min(count, result.size()));
    }

    /**
     * Check if song was recently skipped
     */
    public boolean wasRecentlySkipped(String songPath) {
        int recentCount = Math.min(50, skipHistory.size());
        for (int i = skipHistory.size() - recentCount; i < skipHistory.size(); i++) {
            if (skipHistory.get(i).equals(songPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get recommended mood adjustments based on learning
     */
    public Map<String, Integer> getRecommendedMoodAdjustments() {
        Map<String, Integer> recommendations = new HashMap<>();

        for (Map.Entry<String, Float> entry : moodPreferences.entrySet()) {
            // Convert 0.0-1.0 to 0-100
            int value = Math.round(entry.getValue() * 100);
            recommendations.put(entry.getKey(), value);
        }

        return recommendations;
    }

    /**
     * Reset all learning data
     */
    public void reset() {
        songScores.clear();
        moodPreferences.clear();
        playHistory.clear();
        skipHistory.clear();

        prefs.edit().clear().apply();

        Log.d(TAG, "Learning data reset");
    }

    /**
     * Get statistics about learning
     */
    public String getStats() {
        int positiveScores = 0;
        int negativeScores = 0;

        for (float score : songScores.values()) {
            if (score > 0) positiveScores++;
            else if (score < 0) negativeScores++;
        }

        return String.format(
                "Learning Stats:\n" +
                        "- Songs tracked: %d\n" +
                        "- Positive scores: %d\n" +
                        "- Negative scores: %d\n" +
                        "- Play history: %d items\n" +
                        "- Skip history: %d items\n" +
                        "- Mood preferences: %d learned",
                songScores.size(),
                positiveScores,
                negativeScores,
                playHistory.size(),
                skipHistory.size(),
                moodPreferences.size()
        );
    }
}