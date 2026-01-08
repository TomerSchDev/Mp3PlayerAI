package com.tomersch.mp3playerai.ai;

import static android.os.Build.*;
import static android.os.Build.VERSION.*;
import static android.os.Build.VERSION_CODES.*;
import static com.tomersch.mp3playerai.ai.DBUtils.LOCAL_DB_NAME;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * AI-powered music recommendation engine with LEARNING!
 * Now gets smarter over time based on user behavior
 */
public class AIRecommendationEngine {
    private static final String TAG = "AIRecommendationEngine";
// === Anti-repeat + freshness tuning ===

    // Don’t recommend again for a while after it was recommended (even if not played)
    private static final long RECOMMEND_COOLDOWN_MS = 60L * 60L * 1000L; // 60 minutes

    // Strong penalty window after a song was played (to push new songs)
    private static final long RECENT_PLAY_WINDOW_MS = 30L * 60L * 1000L; // 30 minutes

    // Penalty scale for “recently played”
    private static final float RECENT_PLAY_PENALTY_MULT = 0.35f; // 65% penalty

    // Bonus for songs never played (encourage exploration)
    private static final float NEVER_PLAYED_BONUS_MULT = 1.15f; // +15%
    private static final Float TOP_PERCENT = 0.05f;

    // Keep small memory to avoid repeats even if app restarts during session (optional: persist later)
    private final Map<String, Long> recentlyRecommendedAt = new HashMap<>();
    // AIRecommendationEngine.java (class fields)

    // How long to strongly avoid recently played songs
    private static final long RECENT_WINDOW_MS = 30L * 60L * 1000L; // 30 minutes

    // How much to penalize if within recent window
    private static final float RECENT_PENALTY_MULT = 0.15f; // 85% reduction

    private Context context;
    private SQLiteDatabase database;
    private float[][] audioPCAComponents;  // 128 x 1024
    private float[][] metaPCAComponents;   // 32 x 384

    // 🧠 NEW: AI Learning Manager
    private AILearningManager learningManager;

    // User preference tracking (kept for backward compatibility)
    private Map<String, Float> userPreferences;
    private List<String> recentlyPlayed;
    private List<String> skippedSongs;
    private static final long COOLDOWN_STRONG_MS = 15 * 60 * 1000L; // 15 minutes
    private static final long COOLDOWN_FADE_MS   = 90 * 60 * 1000L; // 90 minutes
    private static final float NEVER_PLAYED_BOOST = 1.12f;         // +12% for “new” songs
    private boolean usingTempDb = false;
    @RequiresApi(api = N)
    private void markRecommendedNow(String path) {
        if (path == null || path.isEmpty()) return;
        recentlyRecommendedAt.put(path, System.currentTimeMillis());

        // cleanup (keep map from growing forever)
        long now = System.currentTimeMillis();
        recentlyRecommendedAt.entrySet().removeIf(e -> (now - e.getValue()) > (RECOMMEND_COOLDOWN_MS * 4));
    }
    private boolean isInRecommendCooldown(String path) {
        Long t = recentlyRecommendedAt.get(path);
        if (t == null) return false;
        return (System.currentTimeMillis() - t) < RECOMMEND_COOLDOWN_MS;
    }
    public AIRecommendationEngine(Context context) {
        this.context = context;
        this.userPreferences = new HashMap<>();
        this.recentlyPlayed = new ArrayList<>();
        this.skippedSongs = new ArrayList<>();

        // 🧠 Initialize learning manager
        this.learningManager = new AILearningManager(context);

        initializeDatabase();
        loadPCAComponents();

        Log.d(TAG, "✨ AI Recommendation Engine initialized with learning!");
        Log.d(TAG, learningManager.getStats());
    }

    /**
     * Initialize the database from assets or local database
     */
    private void initializeDatabase() {
        try {
            // First, try to use local database (has device songs with correct paths)
            File localDbFile = new File(context.getDatabasePath(LOCAL_DB_NAME).getPath());

            if (localDbFile.exists()) {
                Log.d(TAG, "Using local database with device songs");
                database = SQLiteDatabase.openDatabase(
                        localDbFile.getPath(),
                        null,
                        SQLiteDatabase.OPEN_READONLY
                );
                usingTempDb = true;
                Log.d(TAG, "Local database opened successfully");
                return;
            }

            // Fallback to original database
            Log.d(TAG, "Local database not found, using original database");
            File dbFile = DBUtils.copyDatabase(context);
            if(dbFile == null){
                Log.e(TAG, "Error copying database");
                return;
            }
            database = SQLiteDatabase.openDatabase(
                    dbFile.getPath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );
            usingTempDb = false;

            Log.d(TAG, "Database initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing database", e);
        }
    }

    /**
     * Load PCA components from assets
     */
    private void loadPCAComponents() {
        try {
            // Load audio PCA components (128 x 1024)
            InputStream audioStream = context.getAssets().open("audio_pca_components.npy");
            audioPCAComponents = loadNumpyArray(audioStream, 128, 1024);
            audioStream.close();

            // Load meta PCA components (32 x 384)
            InputStream metaStream = context.getAssets().open("meta_pca_components.npy");
            metaPCAComponents = loadNumpyArray(metaStream, 32, 384);
            metaStream.close();

            Log.d(TAG, "PCA components loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error loading PCA components", e);
        }
    }

    /**
     * Load numpy array from .npy file
     */
    private float[][] loadNumpyArray(InputStream stream, int rows, int cols) throws Exception {
        // Skip numpy header (typically 128 bytes)
        stream.skip(128);

        float[][] array = new float[rows][cols];
        byte[] buffer = new byte[cols * 4]; // 4 bytes per float

        for (int i = 0; i < rows; i++) {
            int bytesRead = stream.read(buffer);
            if (bytesRead != buffer.length) {
                break;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            for (int j = 0; j < cols; j++) {
                array[i][j] = byteBuffer.getFloat();
            }
        }

        return array;
    }
    public Set<RecommendedSong> getRecommendations(
            String textQuery,
            Map<String, Integer> moodPreferences,
            int maxResults
    ) {
        if (SDK_INT >= N) {
            return getRecommendations(textQuery, moodPreferences, maxResults, null);
        }
        return new TreeSet<>();
    }

    @RequiresApi(api = N)
    public Set<RecommendedSong> getRecommendations(
            String textQuery,
            Map<String, Integer> moodPreferences,
            int maxResults,
            Set<String> excludePaths
    ) {
        Set<RecommendedSong> all = new HashSet<>();

        if (database == null) {
            Log.e(TAG, "Database not initialized");
            return all;
        }

        try {
            Cursor cursor = database.rawQuery(
                    "SELECT path, title, artist, genre, tags, year, " +
                            "hype, aggressive, melodic, atmospheric, cinematic, rhythmic, " +
                            "audio_blob, meta_blob, filename FROM songs",
                    null
            );

            while (cursor.moveToNext()) {
                String path = cursor.getString(0);
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
                byte[] audioBlob = cursor.getBlob(12);
                byte[] metaBlob = cursor.getBlob(13);
                String filename = cursor.getString(14);

                float score = calculateSimilarityScore(
                        path,
                        textQuery,
                        moodPreferences,
                        audioBlob,
                        metaBlob,
                        hype, aggressive, melodic, atmospheric, cinematic, rhythmic,
                        genre, tags
                );

                score = applyLearningAdjustments(path, score);

                RecommendedSong r = new RecommendedSong();
                r.path = path;
                r.title = title;
                r.artist = artist;
                r.genre = genre;
                r.tags = tags;
                r.year = year;
                r.filename = filename;
                r.score=applyFreshnessBias(path,score);
                all.add(r);
            }

            cursor.close();
            //remove excluded songs
            if (excludePaths != null) {
                all.removeIf(r -> excludePaths.contains(r.path));
            }

            all = all.stream().sorted().collect(Collectors.toSet());


            if (all.isEmpty()) return all;

            // ===== NEW: "Top 5% then random pick" =====
            // Compute bucket size = max( maxResults*3 , 5% of library, minimum 30 )
            int n = all.size();
            int topPercent = Math.max(1, (int) Math.ceil(n * TOP_PERCENT));  // 5%
            int minBucket = 30;
            int desiredBucket = Math.max(minBucket, Math.max(topPercent, maxResults * 3));
            int bucketSize = Math.min(n, desiredBucket);

            List<RecommendedSong> pool = null;
            pool = all.stream().limit(bucketSize).collect(Collectors.toList());

            // Random sample (weighted by score) from the pool
            Set<RecommendedSong> picked = weightedSampleWithoutReplacement(pool, Math.min(maxResults, pool.size()));
            List<RecommendedSong> toShuffle = new ArrayList<>(picked);

            // Optional: final shuffle to avoid always “best-first” display
            Collections.shuffle(toShuffle);

            return toShuffle.stream().collect(Collectors.toSet());

        } catch (Exception e) {
            Log.e(TAG, "Error getting recommendations", e);
        }

        return all;
    }



    private float applyFreshnessBias(String path, float score) {
        if (learningManager == null) return score;

        long lastPlayedAt = 0L;
        try {
            lastPlayedAt = learningManager.getLastPlayedAt(path); // you add this
        } catch (Exception ignored) {}

        // Never played → small exploration bonus
        if (lastPlayedAt <= 0L) {
            return score * NEVER_PLAYED_BONUS_MULT;
        }

        long ageMs = System.currentTimeMillis() - lastPlayedAt;

        // Recently played → strong penalty
        if (ageMs < RECENT_PLAY_WINDOW_MS) {
            // Optional: smooth recovery instead of hard cliff
            // Factor goes from ~RECENT_PLAY_PENALTY_MULT up to 1.0 as it ages out
            float t = Math.max(0f, Math.min(1f, (float) ageMs / (float) RECENT_PLAY_WINDOW_MS));
            float mult = RECENT_PLAY_PENALTY_MULT + (1.0f - RECENT_PLAY_PENALTY_MULT) * t;
            return score * mult;
        }

        return score;
    }

    /**
     * Calculate similarity score for a song
     * 🧠 NEW: Now accepts path parameter for learning
     */
    private float calculateSimilarityScore(
            String path,  // 🧠 NEW parameter
            String textQuery,
            Map<String, Integer> moodPreferences,
            byte[] audioBlob,
            byte[] metaBlob,
            int hype, int aggressive, int melodic, int atmospheric, int cinematic, int rhythmic,
            String genre, String tags
    ) {
        float score = 0.0f;
        int components = 0;

        // 1. Mood preference matching (40% weight)
        if (moodPreferences != null && !moodPreferences.isEmpty()) {
            float moodScore = 0.0f;
            int moodCount = 0;

            if (moodPreferences.containsKey("hype")) {
                moodScore += 1.0f - Math.abs(moodPreferences.get("hype") - hype) / 100.0f;
                moodCount++;
            }
            if (moodPreferences.containsKey("aggressive")) {
                moodScore += 1.0f - Math.abs(moodPreferences.get("aggressive") - aggressive) / 100.0f;
                moodCount++;
            }
            if (moodPreferences.containsKey("melodic")) {
                moodScore += 1.0f - Math.abs(moodPreferences.get("melodic") - melodic) / 100.0f;
                moodCount++;
            }
            if (moodPreferences.containsKey("atmospheric")) {
                moodScore += 1.0f - Math.abs(moodPreferences.get("atmospheric") - atmospheric) / 100.0f;
                moodCount++;
            }
            if (moodPreferences.containsKey("cinematic")) {
                moodScore += 1.0f - Math.abs(moodPreferences.get("cinematic") - cinematic) / 100.0f;
                moodCount++;
            }
            if (moodPreferences.containsKey("rhythmic")) {
                moodScore += 1.0f - Math.abs(moodPreferences.get("rhythmic") - rhythmic) / 100.0f;
                moodCount++;
            }

            if (moodCount > 0) {
                score += (moodScore / moodCount) * 0.4f;
                components++;
            }
        }

        // 2. Text query matching (30% weight)
        if (textQuery != null && !textQuery.trim().isEmpty()) {
            float textScore = calculateTextSimilarity(textQuery, genre, tags);
            score += textScore * 0.3f;
            components++;
        }

        // 3. Audio embedding similarity (20% weight) - placeholder for now
        if (audioBlob != null && audioBlob.length > 0) {
            score += 0.5f * 0.2f;
            components++;
        }

        // 4. Meta embedding similarity (10% weight) - placeholder for now
        if (metaBlob != null && metaBlob.length > 0) {
            score += 0.5f * 0.1f;
            components++;
        }

        // Normalize score
        if (components > 0) {
            return score;
        }

        return 0.0f;
    }
    private static Set<RecommendedSong> weightedSampleWithoutReplacement(
            List<RecommendedSong> pool, int count
    ) {
        if (pool == null || pool.isEmpty() || count <= 0) return new HashSet<>();
        if (count >= pool.size()) return new HashSet<>(pool);

        // Make a mutable copy
        List<RecommendedSong> candidates = new ArrayList<>(pool);
        Set<RecommendedSong> result = new HashSet<>(count);

        // Shift scores positive and avoid zeros
        for (int pick = 0; pick < count && !candidates.isEmpty(); pick++) {
            double total = 0.0;
            for (RecommendedSong r : candidates) {
                double w = Math.max(0.0001, r.score); // ensure >0
                total += w;
            }

            double x = Math.random() * total;
            double run = 0.0;
            int chosenIdx = candidates.size() - 1;

            for (int i = 0; i < candidates.size(); i++) {
                run += Math.max(0.0001, candidates.get(i).score);
                if (run >= x) {
                    chosenIdx = i;
                    break;
                }
            }

            result.add(candidates.remove(chosenIdx));
        }

        return result;
    }
    /**
     * 🧠 NEW: Apply learning adjustments to base score
     */
    private float applyLearningAdjustments(String path, float baseScore) {
        float adjusted = baseScore;

        // 1) Learned preference boost/penalty (your existing behavior)
        float learnedScore = learningManager.getSongScore(path);
        if (learnedScore > 0) {
            adjusted *= (1.0f + learnedScore * 0.5f);   // up to +50%
        } else if (learnedScore < 0) {
            adjusted *= (1.0f + learnedScore * 0.3f);   // penalty
        }

        // 2) Strong penalty if recently skipped (your existing behavior)
        if (learningManager.wasRecentlySkipped(path)) {
            adjusted *= 0.3f;
        }

        // 3) NEW: “just heard” cooldown penalty (time-based)
        long lastPlayedAt = learningManager.getLastPlayedAt(path);
        if (lastPlayedAt > 0) {
            long age = System.currentTimeMillis() - lastPlayedAt;

            if (age <= COOLDOWN_STRONG_MS) {
                // very recent -> crush score
                adjusted *= 0.15f;
            } else if (age <= COOLDOWN_FADE_MS) {
                // fade from 0.15 -> 1.0 linearly
                float t = (float)(age - COOLDOWN_STRONG_MS) / (float)(COOLDOWN_FADE_MS - COOLDOWN_STRONG_MS);
                float factor = 0.15f + 0.85f * t;
                adjusted *= factor;
            }
        } else {
            // 4) NEW: novelty boost for songs never played
            adjusted *= NEVER_PLAYED_BOOST;
        }

        return adjusted;
    }

    /**
     * Calculate text similarity using simple keyword matching
     */
    private float calculateTextSimilarity(String query, String genre, String tags) {
        if (query == null || query.trim().isEmpty()) {
            return 0.0f;
        }

        query = query.toLowerCase();
        String[] keywords = query.split("\\s+");

        float similarity = 0.0f;
        int matches = 0;

        String genreLower = genre != null ? genre.toLowerCase() : "";
        String tagsLower = tags != null ? tags.toLowerCase() : "";

        for (String keyword : keywords) {
            if (keyword.length() < 3) continue;

            if (genreLower.contains(keyword)) {
                similarity += 2.0f;
                matches++;
            }
            if (tagsLower.contains(keyword)) {
                similarity += 1.0f;
                matches++;
            }
        }

        return matches > 0 ? Math.min(similarity / matches, 1.0f) : 0.0f;
    }

    /**
     * Adjust score based on user preferences (kept for backward compatibility)
     */
    private float adjustScoreByUserPreferences(String path, float baseScore) {
        if (skippedSongs.contains(path)) {
            return baseScore * 0.5f;
        }

        if (userPreferences.containsKey(path)) {
            float adjustment = userPreferences.get(path);
            return baseScore * (1.0f + adjustment);
        }

        return baseScore;
    }

    // ===== 🧠 NEW LEARNING METHODS =====

    /**
     * Record that a song was played
     */
    @RequiresApi(api = N)
    public void recordSongPlayed(String path) {
        learningManager.recordSongPlayed(path);

        // Also update old system (backward compatibility)
        if (!recentlyPlayed.contains(path)) {
            recentlyPlayed.add(0, path);
            if (recentlyPlayed.size() > 20) {
                recentlyPlayed.remove(recentlyPlayed.size() - 1);
            }
        }

        float currentPref = userPreferences.getOrDefault(path, 0.0f);
        userPreferences.put(path, Math.min(currentPref + 0.1f, 0.5f));
        skippedSongs.remove(path);
    }

    /**
     * Record that a song was skipped
     */
    @RequiresApi(api = N)
    public void recordSongSkipped(String path) {
        learningManager.recordSongSkipped(path);

        // Also update old system (backward compatibility)
        if (!skippedSongs.contains(path)) {
            skippedSongs.add(path);
            if (skippedSongs.size() > 50) {
                skippedSongs.remove(0);
            }
        }

        float currentPref = userPreferences.getOrDefault(path, 0.0f);
        userPreferences.put(path, Math.max(currentPref - 0.2f, -0.5f));
    }

    /**
     * Record that a song was completed
     */
    public void recordSongCompleted(String path) {
        learningManager.recordSongCompleted(path);
    }

    /**
     * Record that a song was replayed
     */
    public void recordSongReplayed(String path) {
        learningManager.recordSongReplayed(path);
    }

    /**
     * Get learning statistics
     */
    public String getLearningStats() {
        return learningManager.getStats();
    }

    /**
     * Get learning manager (for advanced usage)
     */
    public AILearningManager getLearningManager() {
        return learningManager;
    }

    /**
     * Get quick recommendations based on a mood preset
     */
    public Set<RecommendedSong> getPresetRecommendations(String preset, int maxResults) {
        Map<String, Integer> moodPrefs = new HashMap<>();
        String textQuery = "";

        switch (preset.toLowerCase()) {
            case "energetic":
                moodPrefs.put("hype", 85);
                moodPrefs.put("rhythmic", 80);
                textQuery = "energetic upbeat high energy";
                break;
            case "chill":
                moodPrefs.put("atmospheric", 75);
                moodPrefs.put("melodic", 70);
                moodPrefs.put("hype", 30);
                textQuery = "chill relaxing calm";
                break;
            case "focus":
                moodPrefs.put("atmospheric", 80);
                moodPrefs.put("cinematic", 70);
                moodPrefs.put("aggressive", 20);
                textQuery = "focus concentration ambient";
                break;
            case "workout":
                moodPrefs.put("hype", 90);
                moodPrefs.put("aggressive", 70);
                moodPrefs.put("rhythmic", 85);
                textQuery = "workout intense powerful";
                break;
            case "party":
                moodPrefs.put("hype", 95);
                moodPrefs.put("rhythmic", 90);
                textQuery = "party dance upbeat";
                break;
        }

        return getRecommendations(textQuery, moodPrefs, maxResults);
    }

    /**
     * Close database connection
     */
    public void close() {
        if (database != null && database.isOpen()) {
            database.close();
        }
    }

    /**
     * Check if using temporary database with device songs
     */
    public boolean isUsingTempDatabase() {
        return usingTempDb;
    }

    /**
     * Recommended song result
     */
    public static class RecommendedSong implements Comparable<RecommendedSong> {
        public String path;
        public String title;
        public String artist;
        public String genre;
        public String tags;
        public int year;
        public float score;
        public String filename;

        public Song toSong() {
            Song song = new Song(
                    title != null ? title : "Unknown",
                    artist != null ? artist : "Unknown Artist",
                    path != null ? path : "",
                    0L
            );
            return song;
        }

        public static double getScore(Object o) {
            if (o instanceof RecommendedSong) {
                return ((RecommendedSong) o).score;
            }
            return 0;
        }

        @Override
        public int compareTo(RecommendedSong recommendedSong) {
            return Double.compare(getScore(this), getScore(recommendedSong));
        }
    }
}