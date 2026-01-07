package com.tomersch.mp3playerai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tomersch.mp3playerai.models.Playlist;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages playlists - create, delete, add/remove songs
 */
public class PlaylistManager {

    private static final String TAG = "PlaylistManager";
    private static final String PREFS_NAME = "Playlists";
    private static final String KEY_PLAYLISTS = "playlists";

    private final Context context;
    private SharedPreferences prefs;
    private Gson gson;
    private List<OnPlaylistChangeListener> listeners;

    public interface OnPlaylistChangeListener {
        void onPlaylistsChanged();
    }

    public PlaylistManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.listeners = new ArrayList<>();
    }

    /**
     * Get all playlists
     */
    public List<Playlist> getAllPlaylists() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<Playlist>>(){}.getType();
        List<Playlist> playlists = gson.fromJson(json, type);
        return playlists != null ? playlists : new ArrayList<>();
    }

    /**
     * Save playlists to storage
     */
    private void savePlaylists(List<Playlist> playlists) {
        String json = gson.toJson(playlists);
        prefs.edit().putString(KEY_PLAYLISTS, json).apply();
        notifyListeners();
        Log.d(TAG, "Saved " + playlists.size() + " playlists");
    }

    /**
     * Create a new playlist
     */
    public Playlist createPlaylist(String name) {
        List<Playlist> playlists = getAllPlaylists();

        // Generate unique ID
        String id = UUID.randomUUID().toString();

        Playlist playlist = new Playlist(id, name);
        playlists.add(playlist);

        savePlaylists(playlists);
        Log.d(TAG, "Created playlist: " + name);

        return playlist;
    }

    /**
     * Delete a playlist
     */
    public void deletePlaylist(String playlistId) {
        List<Playlist> playlists = getAllPlaylists();
        playlists.removeIf(p -> p.getId().equals(playlistId));

        savePlaylists(playlists);
        Log.d(TAG, "Deleted playlist: " + playlistId);
    }

    /**
     * Rename a playlist
     */
    public void renamePlaylist(String playlistId, String newName) {
        List<Playlist> playlists = getAllPlaylists();

        for (Playlist playlist : playlists) {
            if (playlist.getId().equals(playlistId)) {
                playlist.setName(newName);
                break;
            }
        }

        savePlaylists(playlists);
        Log.d(TAG, "Renamed playlist: " + playlistId + " to " + newName);
    }

    /**
     * Get a playlist by ID
     */
    public Playlist getPlaylist(String playlistId) {
        List<Playlist> playlists = getAllPlaylists();

        for (Playlist playlist : playlists) {
            if (playlist.getId().equals(playlistId)) {
                return playlist;
            }
        }

        return null;
    }

    /**
     * Add a song to a playlist
     */
    public void addSongToPlaylist(String playlistId, String songPath) {
        List<Playlist> playlists = getAllPlaylists();

        for (Playlist playlist : playlists) {
            if (playlist.getId().equals(playlistId)) {
                playlist.addSong(songPath);
                break;
            }
        }

        savePlaylists(playlists);
        Log.d(TAG, "Added song to playlist: " + playlistId);
    }

    /**
     * Remove a song from a playlist
     */
    public void removeSongFromPlaylist(String playlistId, String songPath) {
        List<Playlist> playlists = getAllPlaylists();

        for (Playlist playlist : playlists) {
            if (playlist.getId().equals(playlistId)) {
                playlist.removeSong(songPath);
                break;
            }
        }

        savePlaylists(playlists);
        Log.d(TAG, "Removed song from playlist: " + playlistId);
    }

    /**
     * Check if a song is in a playlist
     */
    public boolean isSongInPlaylist(String playlistId, String songPath) {
        Playlist playlist = getPlaylist(playlistId);
        return playlist != null && playlist.containsSong(songPath);
    }

    /**
     * Get playlists containing a specific song
     */
    public List<Playlist> getPlaylistsContainingSong(String songPath) {
        List<Playlist> allPlaylists = getAllPlaylists();
        List<Playlist> result = new ArrayList<>();

        for (Playlist playlist : allPlaylists) {
            if (playlist.containsSong(songPath)) {
                result.add(playlist);
            }
        }

        return result;
    }

    /**
     * Add listener for playlist changes
     */
    public void addListener(OnPlaylistChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove listener
     */
    public void removeListener(OnPlaylistChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify all listeners of changes
     */
    private void notifyListeners() {
        for (OnPlaylistChangeListener listener : listeners) {
            listener.onPlaylistsChanged();
        }
    }

    /**
     * Clear all playlists (for testing/debugging)
     */
    public void clearAllPlaylists() {
        prefs.edit().remove(KEY_PLAYLISTS).apply();
        notifyListeners();
        Log.d(TAG, "Cleared all playlists");
    }
}
