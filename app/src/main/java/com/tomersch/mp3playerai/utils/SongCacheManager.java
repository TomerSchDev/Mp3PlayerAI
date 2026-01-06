package com.tomersch.mp3playerai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages song cache to avoid rescanning unchanged files
 */
public class SongCacheManager {

    private static final String TAG = "SongCacheManager";
    private static final String PREFS_NAME = "SongCache";
    private static final String KEY_SONGS = "cached_songs";

    private Context context;
    private SharedPreferences prefs;
    private Gson gson;

    public SongCacheManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    /**
     * Save songs to cache
     */
    public void saveSongs(List<Song> songs) {
        String json = gson.toJson(songs);
        prefs.edit().putString(KEY_SONGS, json).apply();
        Log.d(TAG, "Saved " + songs.size() + " songs to cache");
    }

    /**
     * Load songs from cache
     */
    public List<Song> loadCachedSongs() {
        String json = prefs.getString(KEY_SONGS, null);
        if (json == null) {
            Log.d(TAG, "No cached songs found");
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<Song>>(){}.getType();
        List<Song> songs = gson.fromJson(json, type);
        Log.d(TAG, "Loaded " + songs.size() + " songs from cache");
        return songs != null ? songs : new ArrayList<>();
    }

    /**
     * Compare current scan with cached songs and return smart update
     * @param newSongs Songs found in current scan
     * @return ScanResult with added, removed, and all songs
     */
    public ScanResult compareAndUpdate(List<Song> newSongs) {
        List<Song> cachedSongs = loadCachedSongs();

        // Create maps for quick lookup
        Map<String, Song> cachedMap = new HashMap<>();
        for (Song song : cachedSongs) {
            cachedMap.put(song.getPath(), song);
        }

        Map<String, Song> newMap = new HashMap<>();
        for (Song song : newSongs) {
            newMap.put(song.getPath(), song);
        }

        // Find added songs (in new but not in cached)
        List<Song> addedSongs = new ArrayList<>();
        for (Song song : newSongs) {
            if (!cachedMap.containsKey(song.getPath())) {
                addedSongs.add(song);
            }
        }

        // Find removed songs (in cached but not in new, and file doesn't exist)
        List<Song> removedSongs = new ArrayList<>();
        for (Song song : cachedSongs) {
            if (!newMap.containsKey(song.getPath())) {
                // Double-check file actually doesn't exist
                File file = new File(song.getPath());
                if (!file.exists()) {
                    removedSongs.add(song);
                }
            }
        }

        // Build final song list (keep cached + add new)
        List<Song> finalSongs = new ArrayList<>();

        // Add existing songs that still exist
        for (Song song : cachedSongs) {
            if (newMap.containsKey(song.getPath()) || new File(song.getPath()).exists()) {
                finalSongs.add(song);
            }
        }

        // Add new songs
        for (Song song : addedSongs) {
            if (!cachedMap.containsKey(song.getPath())) {
                finalSongs.add(song);
            }
        }

        // Save updated list
        saveSongs(finalSongs);

        Log.d(TAG, "Scan comparison:");
        Log.d(TAG, "  Cached songs: " + cachedSongs.size());
        Log.d(TAG, "  New scan found: " + newSongs.size());
        Log.d(TAG, "  Added: " + addedSongs.size());
        Log.d(TAG, "  Removed: " + removedSongs.size());
        Log.d(TAG, "  Final total: " + finalSongs.size());

        return new ScanResult(finalSongs, addedSongs, removedSongs);
    }

    /**
     * Check if we have cached songs
     */
    public boolean hasCachedSongs() {
        return prefs.contains(KEY_SONGS);
    }

    /**
     * Clear all cached songs
     */
    public void clearCache() {
        prefs.edit().remove(KEY_SONGS).apply();
        Log.d(TAG, "Cache cleared");
    }

    /**
     * Result of scan comparison
     */
    public static class ScanResult {
        private List<Song> allSongs;
        private List<Song> addedSongs;
        private List<Song> removedSongs;

        public ScanResult(List<Song> allSongs, List<Song> addedSongs, List<Song> removedSongs) {
            this.allSongs = allSongs;
            this.addedSongs = addedSongs;
            this.removedSongs = removedSongs;
        }

        public List<Song> getAllSongs() {
            return allSongs;
        }

        public List<Song> getAddedSongs() {
            return addedSongs;
        }

        public List<Song> getRemovedSongs() {
            return removedSongs;
        }

        public boolean hasChanges() {
            return !addedSongs.isEmpty() || !removedSongs.isEmpty();
        }
    }
}