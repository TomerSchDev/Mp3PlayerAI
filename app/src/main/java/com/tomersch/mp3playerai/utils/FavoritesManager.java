package com.tomersch.mp3playerai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages favorite songs
 */
public class FavoritesManager {

    private static final String TAG = "FavoritesManager";
    private static final String PREFS_NAME = "Favorites";
    private static final String KEY_FAVORITE_PATHS = "favorite_paths";

    private Context context;
    private SharedPreferences prefs;
    private Set<String> favoritePaths;
    private List<OnFavoritesChangeListener> listeners;

    public interface OnFavoritesChangeListener {
        void onFavoritesChanged();
    }

    public FavoritesManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.listeners = new ArrayList<>();
        loadFavorites();
    }

    /**
     * Load favorites from SharedPreferences
     */
    private void loadFavorites() {
        Set<String> savedFavorites = prefs.getStringSet(KEY_FAVORITE_PATHS, null);
        if (savedFavorites != null) {
            favoritePaths = new HashSet<>(savedFavorites);
        } else {
            favoritePaths = new HashSet<>();
        }
        Log.d(TAG, "Loaded " + favoritePaths.size() + " favorites");
    }

    /**
     * Save favorites to SharedPreferences
     */
    private void saveFavorites() {
        prefs.edit().putStringSet(KEY_FAVORITE_PATHS, favoritePaths).apply();
        notifyListeners();
        Log.d(TAG, "Saved " + favoritePaths.size() + " favorites");
    }

    /**
     * Add a song to favorites
     */
    public void addFavorite(String songPath) {
        if (!favoritePaths.contains(songPath)) {
            favoritePaths.add(songPath);
            saveFavorites();
            Log.d(TAG, "Added to favorites: " + songPath);
        }
    }

    /**
     * Remove a song from favorites
     */
    public void removeFavorite(String songPath) {
        if (favoritePaths.contains(songPath)) {
            favoritePaths.remove(songPath);
            saveFavorites();
            Log.d(TAG, "Removed from favorites: " + songPath);
        }
    }

    /**
     * Toggle favorite status
     */
    public boolean toggleFavorite(String songPath) {
        if (isFavorite(songPath)) {
            removeFavorite(songPath);
            return false;
        } else {
            addFavorite(songPath);
            return true;
        }
    }

    /**
     * Check if a song is favorite
     */
    public boolean isFavorite(String songPath) {
        return favoritePaths.contains(songPath);
    }

    /**
     * Get all favorite song paths
     */
    public Set<String> getFavoritePaths() {
        return new HashSet<>(favoritePaths);
    }

    /**
     * Get count of favorites
     */
    public int getFavoriteCount() {
        return favoritePaths.size();
    }

    /**
     * Clear all favorites
     */
    public void clearAllFavorites() {
        favoritePaths.clear();
        saveFavorites();
        Log.d(TAG, "Cleared all favorites");
    }

    /**
     * Add listener for favorites changes
     */
    public void addListener(OnFavoritesChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove listener
     */
    public void removeListener(OnFavoritesChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify all listeners of changes
     */
    private void notifyListeners() {
        for (OnFavoritesChangeListener listener : listeners) {
            listener.onFavoritesChanged();
        }
    }
}