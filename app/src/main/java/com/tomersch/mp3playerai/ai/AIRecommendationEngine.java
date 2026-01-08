package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI-powered music recommendation engine using embeddings and PCA components
 */
public class AIRecommendationEngine {
    private static final String TAG = "AIRecommendationEngine";
    private static final String DB_NAME = "music_vectors_ai.db";
    private static final String LOCAL_DB_NAME = "music_vectors_local.db";

    private Context context;
    private SQLiteDatabase database;
    private float[][] audioPCAComponents;  // 128 x 1024
    private float[][] metaPCAComponents;   // 32 x 384

    // User preference tracking
    private Map<String, Float> userPreferences;
    private List<String> recentlyPlayed;
    private List<String> skippedSongs;
    private boolean usingTempDb = false;

    public AIRecommendationEngine(Context context) {
        this.context = context;
        this.userPreferences = new HashMap<>();
        this.recentlyPlayed = new ArrayList<>();
        this.skippedSongs = new ArrayList<>();

        initializeDatabase();
        loadPCAComponents();
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

    /**
     * Get song recommendations based on text query and preferences
     */
    public List<RecommendedSong> getRecommendations(
            String textQuery,
            Map<String, Integer> moodPreferences,
            int maxResults
    ) {
        List<RecommendedSong> recommendations = new ArrayList<>();

        if (database == null) {
            Log.e(TAG, "Database not initialized");
            return recommendations;
        }

        try {
            // Query all songs with embeddings
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

                // Calculate similarity score
                float score = calculateSimilarityScore(
                        textQuery,
                        moodPreferences,
                        audioBlob,
                        metaBlob,
                        hype, aggressive, melodic, atmospheric, cinematic, rhythmic,
                        genre, tags
                );

                // Apply user preference adjustments
                score = adjustScoreByUserPreferences(path, score);

                RecommendedSong recSong = new RecommendedSong();
                recSong.path = path;
                recSong.title = title;
                recSong.artist = artist;
                recSong.genre = genre;
                recSong.tags = tags;
                recSong.year = year;
                recSong.score = score;
                recSong.filename = filename;

                recommendations.add(recSong);
            }

            cursor.close();

            // Sort by score descending
            Collections.sort(recommendations, new Comparator<RecommendedSong>() {
                @Override
                public int compare(RecommendedSong a, RecommendedSong b) {
                    return Float.compare(b.score, a.score);
                }
            });

            // Return top results
            if (recommendations.size() > maxResults) {
                recommendations = recommendations.subList(0, maxResults);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting recommendations", e);
        }

        return recommendations;
    }

    /**
     * Calculate similarity score for a song
     */
    private float calculateSimilarityScore(
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
        // TODO: Implement when we have query embeddings
        if (audioBlob != null && audioBlob.length > 0) {
            // For now, use a baseline score
            score += 0.5f * 0.2f;
            components++;
        }

        // 4. Meta embedding similarity (10% weight) - placeholder for now
        if (metaBlob != null && metaBlob.length > 0) {
            // For now, use a baseline score
            score += 0.5f * 0.1f;
            components++;
        }

        // Normalize score
        if (components > 0) {
            return score;
        }

        return 0.0f;
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
            if (keyword.length() < 3) continue; // Skip short words

            if (genreLower.contains(keyword)) {
                similarity += 2.0f; // Genre match is worth more
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
     * Adjust score based on user preferences (skip/play history)
     */
    private float adjustScoreByUserPreferences(String path, float baseScore) {
        // Penalize recently skipped songs
        if (skippedSongs.contains(path)) {
            return baseScore * 0.5f;
        }

        // Boost songs from preferred genres/artists
        if (userPreferences.containsKey(path)) {
            float adjustment = userPreferences.get(path);
            return baseScore * (1.0f + adjustment);
        }

        return baseScore;
    }

    /**
     * Record that a song was played
     */
    public void recordSongPlayed(String path) {
        if (!recentlyPlayed.contains(path)) {
            recentlyPlayed.add(0, path);

            // Keep only recent 20 songs
            if (recentlyPlayed.size() > 20) {
                recentlyPlayed.remove(recentlyPlayed.size() - 1);
            }
        }

        // Boost preference for this song
        float currentPref = userPreferences.getOrDefault(path, 0.0f);
        userPreferences.put(path, Math.min(currentPref + 0.1f, 0.5f));

        // Remove from skipped if it was there
        skippedSongs.remove(path);
    }

    /**
     * Record that a song was skipped
     */
    public void recordSongSkipped(String path) {
        if (!skippedSongs.contains(path)) {
            skippedSongs.add(path);

            // Keep only recent 50 skipped songs
            if (skippedSongs.size() > 50) {
                skippedSongs.remove(0);
            }
        }

        // Reduce preference for this song
        float currentPref = userPreferences.getOrDefault(path, 0.0f);
        userPreferences.put(path, Math.max(currentPref - 0.2f, -0.5f));
    }

    /**
     * Get quick recommendations based on a mood preset
     */
    public List<RecommendedSong> getPresetRecommendations(String preset, int maxResults) {
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
            // Use the proper Song constructor: (title, artist, path, duration)
            Song song = new Song(
                    title != null ? title : "Unknown",
                    artist != null ? artist : "Unknown Artist",
                    path != null ? path : "",
                    0L  // Duration not available in database, set to 0
            );
            return song;
        }
    }
}