package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI recommender:
 * - On-device LLM parses query -> QueryProfile
 * - Score all songs against profile + learning + freshness
 * - Take top X% pool, then randomly pick (weighted) without replacement
 * - Persist "recommended cooldown" to prevent returning the same songs every time
 */
public class AIRecommendationEngine implements AutoCloseable {
    private static final String TAG = "AIRecommendationEngine";

    // Prevent repeating recommendations across calls (persisted)
    private static final long RECOMMEND_COOLDOWN_MS = 60L * 60L * 1000L; // 60 min

    private final Context appContext;
    private final SQLiteDatabase database;
    private final AILearningManager learningManager;

    // Reusable LLM parser (you can also use for playlist naming)
    private final LocalLlmInterpreter llmParser;

    public AIRecommendationEngine(Context context, String modelTaskPath) {
        this.appContext = context.getApplicationContext();
        this.learningManager = new AILearningManager(appContext);

        this.database = openDatabaseOrThrow(appContext);

        // LLM parser is separate and reusable
        this.llmParser = new LocalLlmInterpreter(appContext, modelTaskPath);

        Log.d(TAG, "Initialized. " + learningManager.getStats());
    }

    private static SQLiteDatabase openDatabaseOrThrow(Context ctx) {
        // You already have DBUtils logic; keep your preferred approach.
        // This version assumes DBUtils.copyDatabase(ctx) returns a valid File.
        try {
            File dbFile = DBUtils.copyDatabase(ctx);
            if (dbFile == null) throw new IllegalStateException("DB copy failed (dbFile=null)");
            return SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open DB", e);
        }
    }

    public List<RecommendedSong> getRecommendations(
            String textQuery,
            int maxResults,
            Set<String> excludePaths
    ) {
        if (maxResults <= 0) return new ArrayList<>();
        if (excludePaths == null) excludePaths = new HashSet<>();

        // LLM -> structured vector
        QueryProfile profile = llmParser.parseQuery(textQuery);

        // Debug: confirm DB has rows (helps with your “cursor loop ends immediately” issue)
        int totalRows = getSongCountSafe();
        if (totalRows <= 0) {
            Log.e(TAG, "DB has 0 songs. Check DB path/table name.");
            return new ArrayList<>();
        }

        List<RecommendedSong> candidates = new ArrayList<>(Math.min(totalRows, 5000));

        // Pull only what you need.
        // You can extend with meta/audio embedding blobs later.
        try (Cursor cursor = database.rawQuery(
                "SELECT path, title, artist, genre, tags, year, " +
                        "hype, aggressive, melodic, atmospheric, cinematic, rhythmic, filename " +
                        "FROM songs",
                null
        )) {

            while (cursor.moveToNext()) {
                String path = cursor.getString(0);
                if (path == null) continue;

                // Hard excludes (queue, session filters, etc.)
                if (excludePaths.contains(path)) continue;

                // Persisted anti-repeat: recommended cooldown
                if (isInRecommendCooldown(path)) continue;

                // Avoid very recent plays (from profile)
                if (wasPlayedRecently(path, profile.avoidRecentMinutes)) continue;

                String title = cursor.getString(1);
                String artist = cursor.getString(2);
                String genre = cursor.getString(3);
                String tags = cursor.getString(4);
                int year = cursor.getInt(5);

                int hype = cursor.getInt(6);
                int aggressive = cursor.getInt(7);
                int melodic = cursor.getInt(8);
                int atmospheric = cursor.getInt(9);
                int cinematic = cursor.getInt(10);
                int rhythmic = cursor.getInt(11);

                String filename = cursor.getString(12);

                float score = 0f;

                // 1) Mood match
                score += moodScore(profile.moods, hype, aggressive, melodic, atmospheric, cinematic, rhythmic) * 0.45f;

                // 2) Keyword match (LLM-provided)
                score += keywordScore(profile.keywords, title, artist, genre, tags, filename) * 0.35f;

                // 3) Learning preference (like/dislike)
                score = applyLearning(path, score);

                // 4) Freshness exploration: never played gets a small boost
                score = applyNovelty(path, score);

                if (score <= 0f) continue;

                RecommendedSong r = new RecommendedSong();
                r.path = path;
                r.title = (title != null && !title.isEmpty()) ? title : "Unknown";
                r.artist = (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
                r.genre = genre;
                r.tags = tags;
                r.year = year;
                r.filename = filename;
                r.score = score;

                candidates.add(r);
            }
        } catch (Exception e) {
            Log.e(TAG, "Recommendation query failed", e);
            return new ArrayList<>();
        }

        if (candidates.isEmpty()) {
            Log.w(TAG, "No candidates after filters. Consider relaxing cooldowns.");
            return new ArrayList<>();
        }

        // Sort DESC by score
        candidates.sort((a, b) -> Float.compare(b.score, a.score));

        // Build top-percent pool
        int n = candidates.size();
        int byPercent = Math.max(1, (int) Math.ceil(n * profile.topPercent));
        int minPool = 50;
        int byNeed = maxResults * 5;
        int poolSize = Math.min(n, Math.max(minPool, Math.max(byPercent, byNeed)));

        List<RecommendedSong> pool = new ArrayList<>(candidates.subList(0, poolSize));

        // Weighted random without replacement from the pool
        List<RecommendedSong> picked = weightedPick(pool, Math.min(maxResults, pool.size()), profile.temperature);

        // Persist "recommended now" so next call won't return the same set
        for (RecommendedSong r : picked) {
            learningManager.recordSongRecommended(r.path);
        }

        // Optional shuffle so UI doesn’t look ordered
        Collections.shuffle(picked);

        return picked;
    }

    private int getSongCountSafe() {
        Cursor c = null;
        try {
            c = database.rawQuery("SELECT COUNT(*) FROM songs", null);
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "COUNT(*) failed", e);
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean isInRecommendCooldown(String path) {
        long t = learningManager.getLastRecommendedAt(path);
        if (t <= 0) return false;
        return (System.currentTimeMillis() - t) < RECOMMEND_COOLDOWN_MS;
    }

    private boolean wasPlayedRecently(String path, int avoidRecentMinutes) {
        long last = learningManager.getLastPlayedAt(path);
        if (last <= 0) return false;
        long windowMs = Math.max(5, avoidRecentMinutes) * 60L * 1000L;
        return (System.currentTimeMillis() - last) < windowMs;
    }

    private float applyLearning(String path, float base) {
        float learned = learningManager.getSongScore(path); // -1..+1
        float out = base;

        if (learned > 0f) out *= (1.0f + learned * 0.50f);
        else if (learned < 0f) out *= (1.0f + learned * 0.35f);

        if (learningManager.wasRecentlySkipped(path)) out *= 0.30f;

        return out;
    }

    private float applyNovelty(String path, float score) {
        long last = learningManager.getLastPlayedAt(path);
        if (last <= 0) {
            // never played -> exploration bonus
            return score * 1.12f;
        }
        return score;
    }

    private static float moodScore(
            Map<String, Integer> moods,
            int hype, int aggressive, int melodic, int atmospheric, int cinematic, int rhythmic
    ) {
        if (moods == null || moods.isEmpty()) return 0f;

        float sum = 0f;
        int count = 0;

        sum += oneMood(moods, "hype", hype); count++;
        sum += oneMood(moods, "aggressive", aggressive); count++;
        sum += oneMood(moods, "melodic", melodic); count++;
        sum += oneMood(moods, "atmospheric", atmospheric); count++;
        sum += oneMood(moods, "cinematic", cinematic); count++;
        sum += oneMood(moods, "rhythmic", rhythmic); count++;

        return (count > 0) ? (sum / count) : 0f;
    }

    private static float oneMood(Map<String, Integer> moods, String key, int val) {
        int target = moods.getOrDefault(key, 50);
        float diff = Math.abs(target - val) / 100f;
        return 1.0f - diff; // 1 best, 0 worst
    }

    private static float keywordScore(
            List<String> keywords,
            String title,
            String artist,
            String genre,
            String tags,
            String filename
    ) {
        if (keywords == null || keywords.isEmpty()) return 0f;

        String t = safeLower(title);
        String a = safeLower(artist);
        String g = safeLower(genre);
        String ta = safeLower(tags);
        String f = safeLower(filename);

        int hits = 0;
        int total = 0;

        for (String k : keywords) {
            String kw = (k == null) ? "" : k.trim().toLowerCase();
            if (kw.length() < 3) continue;

            total++;

            boolean matched =
                    t.contains(kw) ||
                            a.contains(kw) ||
                            g.contains(kw) ||
                            ta.contains(kw) ||
                            f.contains(kw);

            if (matched) hits++;
        }

        if (total == 0) return 0f;
        return Math.min(1.0f, (float) hits / (float) total);
    }

    private static String safeLower(String s) {
        return (s == null) ? "" : s.toLowerCase();
    }

    /**
     * Weighted random pick without replacement.
     * Uses temperature to control randomness:
     * - lower temp => more greedy
     * - higher temp => more random within pool
     */
    private static List<RecommendedSong> weightedPick(List<RecommendedSong> pool, int count, float temperature) {
        if (pool == null || pool.isEmpty() || count <= 0) return new ArrayList<>();
        if (count >= pool.size()) return new ArrayList<>(pool);

        float temp = Math.max(0.10f, Math.min(1.50f, temperature));

        List<RecommendedSong> candidates = new ArrayList<>(pool);
        List<RecommendedSong> result = new ArrayList<>(count);

        for (int pick = 0; pick < count && !candidates.isEmpty(); pick++) {
            double totalW = 0.0;

            // Softmax-like weighting: w = exp(score/temp)
            for (RecommendedSong r : candidates) {
                double w = Math.exp(Math.max(0.0, r.score) / temp);
                totalW += w;
            }

            double x = Math.random() * totalW;
            double run = 0.0;
            int chosen = candidates.size() - 1;

            for (int i = 0; i < candidates.size(); i++) {
                RecommendedSong r = candidates.get(i);
                run += Math.exp(Math.max(0.0, r.score) / temp);
                if (run >= x) {
                    chosen = i;
                    break;
                }
            }

            result.add(candidates.remove(chosen));
        }

        return result;
    }

    @Override
    public void close() {
        try { llmParser.close(); } catch (Exception ignored) {}
        try { database.close(); } catch (Exception ignored) {}
    }

    public void recordSongCompleted(String currentSongPath) {
        learningManager.recordSongCompleted(currentSongPath);
    }

    public String getLearningStats() {
        return learningManager.getStats();
    }

    public void recordSongPlayed(String path) {
        learningManager.recordSongPlayed(path);
    }

    public void recordSongSkipped(String currentSongPath) {
        learningManager.recordSongSkipped(currentSongPath);
    }

    public void recordSongReplayed(String currentSongPath) {
        learningManager.recordSongReplayed(currentSongPath);
    }

    public AILearningManager getLearningManager() {
        return learningManager;
    }

    // Result class
    public static class RecommendedSong {
        public String path;
        public String title;
        public String artist;
        public String genre;
        public String tags;
        public int year;
        public float score;
        public String filename;

        public Song toSong() {
            return new Song(
                    title != null ? title : "Unknown",
                    artist != null ? artist : "Unknown Artist",
                    path != null ? path : "",
                    0L
            );
        }
    }
}
