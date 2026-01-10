package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Persistent learning + anti-repeat memory.
 * Stores:
 * - songScores: like/dislike
 * - playHistory/skipHistory
 * - lastPlayedAt: time-based freshness penalty
 * - lastRecommendedAt: recommendation cooldown (prevents "same songs always")
 */
public class AILearningManager {
    private static final String TAG = "AILearningManager";
    private static final String PREFS_NAME = "ai_learning_prefs";

    private static final String KEY_PLAY_HISTORY = "play_history";
    private static final String KEY_SKIP_HISTORY = "skip_history";
    private static final String KEY_SONG_SCORES = "song_scores";
    private static final String KEY_MOOD_PREFERENCES = "mood_preferences";

    private static final String KEY_LAST_PLAYED_AT = "last_played_at";
    private static final String KEY_LAST_RECOMMENDED_AT = "last_recommended_at";

    private static final int MAX_HISTORY_SIZE = 500;

    private static final float PLAY_BOOST = 0.15f;
    private static final float SKIP_PENALTY = -0.25f;
    private static final float COMPLETE_BOOST = 0.30f;
    private static final float REPLAY_BOOST = 0.20f;

    private final SharedPreferences prefs;

    private final Map<String, Float> songScores = new HashMap<>();
    private final Map<String, Float> moodPreferences = new HashMap<>();
    private final List<String> playHistory = new ArrayList<>();
    private final List<String> skipHistory = new ArrayList<>();

    private final Map<String, Long> lastPlayedAt = new HashMap<>();
    private final Map<String, Long> lastRecommendedAt = new HashMap<>();

    public AILearningManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        try {
            // scores
            JSONObject scoresObj = new JSONObject(prefs.getString(KEY_SONG_SCORES, "{}"));
            for (Iterator<String> it = scoresObj.keys(); it.hasNext(); ) {
                String k = it.next();
                songScores.put(k, (float) scoresObj.getDouble(k));
            }

            // mood prefs
            JSONObject moodObj = new JSONObject(prefs.getString(KEY_MOOD_PREFERENCES, "{}"));
            for (Iterator<String> it = moodObj.keys(); it.hasNext(); ) {
                String k = it.next();
                moodPreferences.put(k, (float) moodObj.getDouble(k));
            }

            // history
            JSONArray playArr = new JSONArray(prefs.getString(KEY_PLAY_HISTORY, "[]"));
            for (int i = 0; i < playArr.length(); i++) playHistory.add(playArr.getString(i));

            JSONArray skipArr = new JSONArray(prefs.getString(KEY_SKIP_HISTORY, "[]"));
            for (int i = 0; i < skipArr.length(); i++) skipHistory.add(skipArr.getString(i));

            // lastPlayedAt
            JSONObject lp = new JSONObject(prefs.getString(KEY_LAST_PLAYED_AT, "{}"));
            for (Iterator<String> it = lp.keys(); it.hasNext(); ) {
                String k = it.next();
                lastPlayedAt.put(k, lp.getLong(k));
            }

            // lastRecommendedAt
            JSONObject lr = new JSONObject(prefs.getString(KEY_LAST_RECOMMENDED_AT, "{}"));
            for (Iterator<String> it = lr.keys(); it.hasNext(); ) {
                String k = it.next();
                lastRecommendedAt.put(k, lr.getLong(k));
            }

            Log.d(TAG, "Loaded learning: scores=" + songScores.size() +
                    ", plays=" + playHistory.size() +
                    ", lastPlayed=" + lastPlayedAt.size() +
                    ", lastRecommended=" + lastRecommendedAt.size());

        } catch (Exception e) {
            Log.e(TAG, "Load failed", e);
        }
    }

    private void save() {
        try {
            SharedPreferences.Editor editor = prefs.edit();

            JSONObject scoresObj = new JSONObject();
            for (Map.Entry<String, Float> e : songScores.entrySet()) scoresObj.put(e.getKey(), e.getValue());
            editor.putString(KEY_SONG_SCORES, scoresObj.toString());

            JSONObject moodObj = new JSONObject();
            for (Map.Entry<String, Float> e : moodPreferences.entrySet()) moodObj.put(e.getKey(), e.getValue());
            editor.putString(KEY_MOOD_PREFERENCES, moodObj.toString());

            JSONArray playArr = new JSONArray();
            int ps = Math.max(0, playHistory.size() - MAX_HISTORY_SIZE);
            for (int i = ps; i < playHistory.size(); i++) playArr.put(playHistory.get(i));
            editor.putString(KEY_PLAY_HISTORY, playArr.toString());

            JSONArray skipArr = new JSONArray();
            int ss = Math.max(0, skipHistory.size() - MAX_HISTORY_SIZE);
            for (int i = ss; i < skipHistory.size(); i++) skipArr.put(skipHistory.get(i));
            editor.putString(KEY_SKIP_HISTORY, skipArr.toString());

            JSONObject lp = new JSONObject();
            for (Map.Entry<String, Long> e : lastPlayedAt.entrySet()) lp.put(e.getKey(), e.getValue());
            editor.putString(KEY_LAST_PLAYED_AT, lp.toString());

            JSONObject lr = new JSONObject();
            for (Map.Entry<String, Long> e : lastRecommendedAt.entrySet()) lr.put(e.getKey(), e.getValue());
            editor.putString(KEY_LAST_RECOMMENDED_AT, lr.toString());

            editor.apply();

        } catch (Exception e) {
            Log.e(TAG, "Save failed", e);
        }
    }

    public long getLastPlayedAt(String path) {
        Long t = lastPlayedAt.get(path);
        return (t != null) ? t : 0L;
    }

    public long getLastRecommendedAt(String path) {
        Long t = lastRecommendedAt.get(path);
        return (t != null) ? t : 0L;
    }

    public void recordSongRecommended(String path) {
        if (path == null || path.isEmpty()) return;
        lastRecommendedAt.put(path, System.currentTimeMillis());
        save();
    }

    public void recordSongPlayed(String songPath) {
        playHistory.add(songPath);

        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.min(currentScore + PLAY_BOOST, 1.0f));

        skipHistory.remove(songPath);

        lastPlayedAt.put(songPath, System.currentTimeMillis());

        save();
        Log.d(TAG, "Played: " + songPath + " score=" + songScores.get(songPath));
    }

    public void recordSongSkipped(String songPath) {
        skipHistory.add(songPath);

        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.max(currentScore + SKIP_PENALTY, -1.0f));

        save();
        Log.d(TAG, "Skipped: " + songPath + " score=" + songScores.get(songPath));
    }

    public void recordSongCompleted(String songPath) {
        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.min(currentScore + COMPLETE_BOOST, 1.0f));
        save();
    }

    public void recordSongReplayed(String songPath) {
        float currentScore = songScores.getOrDefault(songPath, 0.0f);
        songScores.put(songPath, Math.min(currentScore + REPLAY_BOOST, 1.0f));
        save();
    }

    public float getSongScore(String songPath) {
        return songScores.getOrDefault(songPath, 0.0f);
    }

    public boolean wasRecentlySkipped(String songPath) {
        int recentCount = Math.min(50, skipHistory.size());
        for (int i = skipHistory.size() - recentCount; i < skipHistory.size(); i++) {
            if (i >= 0 && skipHistory.get(i).equals(songPath)) return true;
        }
        return false;
    }

    public Map<String, Integer> getRecommendedMoodAdjustments() {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, Float> e : moodPreferences.entrySet()) {
            out.put(e.getKey(), Math.round(e.getValue() * 100));
        }
        return out;
    }

    public void reset() {
        songScores.clear();
        moodPreferences.clear();
        playHistory.clear();
        skipHistory.clear();
        lastPlayedAt.clear();
        lastRecommendedAt.clear();
        prefs.edit().clear().apply();
    }

    public String getStats() {
        int pos = 0, neg = 0;
        for (float s : songScores.values()) {
            if (s > 0) pos++;
            else if (s < 0) neg++;
        }
        return "Learning Stats:\n" +
                "- Songs tracked: " + songScores.size() + "\n" +
                "- Positive: " + pos + "\n" +
                "- Negative: " + neg + "\n" +
                "- Play history: " + playHistory.size() + "\n" +
                "- Skip history: " + skipHistory.size() + "\n" +
                "- lastPlayedAt: " + lastPlayedAt.size() + "\n" +
                "- lastRecommendedAt: " + lastRecommendedAt.size();
    }

    public Map<String, Float> getAllSongScores() {
        return songScores;
    }

    public void setSongScore(String songPath, float newScore) {
        songScores.put(songPath, newScore);
        save();
    }
}
