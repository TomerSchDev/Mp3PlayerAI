package com.tomersch.mp3playerai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tomersch.mp3playerai.models.Playlist;
import com.tomersch.mp3playerai.models.Song;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Single source of truth for:
 * - Music library
 * - Favorites
 * - Playlists
 */
public class LibraryRepository {

    private static final String TAG = "LibraryRepository";

    private static final String PREFS_NAME = "MusicLibrary";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_PLAYLISTS = "playlists";

    private static LibraryRepository instance;

    public static synchronized LibraryRepository getInstance(Context context) {
        if (instance == null) {
            instance = new LibraryRepository(context.getApplicationContext());
        }
        return instance;
    }

    // --- CORE DATA ---
    private final Map<String, Song> songMap = new HashMap<>();
    private final List<Song> allSongs = new ArrayList<>();
    private final Set<String> favoritePaths = new HashSet<>();
    private final List<Playlist> playlists = new ArrayList<>();

    // --- STORAGE ---
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    // --- LISTENERS ---
    private final List<Runnable> listeners = new ArrayList<>();

    private LibraryRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFavorites();
        loadPlaylists();
    }

    /* ============================================================
       INITIALIZATION
       ============================================================ */

    public void initSongs(List<Song> songs) {
        allSongs.clear();
        songMap.clear();

        allSongs.addAll(songs);
        for (Song song : songs) {
            songMap.put(song.getPath(), song);
        }

        notifyListeners();
        Log.d(TAG, "Initialized library with " + songs.size() + " songs");
    }

    /* ============================================================
       SONG ACCESS
       ============================================================ */

    public List<Song> getAllSongs() {
        return new ArrayList<>(allSongs);
    }

    public Song getSongByPath(String path) {
        return songMap.get(path);
    }

    public List<Song> getSongsInFolder(String folderPath) {
        List<Song> result = new ArrayList<>();
        for (Song song : allSongs) {
            if (song.getPath().startsWith(folderPath)) {
                result.add(song);
            }
        }
        return result;
    }

    /* ============================================================
       FAVORITES
       ============================================================ */

    public boolean isFavorite(Song song) {
        return favoritePaths.contains(song.getPath());
    }

    public boolean toggleFavorite(Song song) {
        boolean added;
        if (favoritePaths.contains(song.getPath())) {
            favoritePaths.remove(song.getPath());
            added = false;
        } else {
            favoritePaths.add(song.getPath());
            added = true;
        }
        saveFavorites();
        return added;
    }

    public List<Song> getFavoriteSongs() {
        List<Song> result = new ArrayList<>();
        for (String path : favoritePaths) {
            Song song = songMap.get(path);
            if (song != null) result.add(song);
        }
        return result;
    }

    private void loadFavorites() {
        Set<String> stored = prefs.getStringSet(KEY_FAVORITES, null);
        if (stored != null) favoritePaths.addAll(stored);
    }

    private void saveFavorites() {
        prefs.edit().putStringSet(KEY_FAVORITES, new HashSet<>(favoritePaths)).apply();
        notifyListeners();
    }

    /* ============================================================
       PLAYLISTS
       ============================================================ */

    public List<Playlist> getPlaylists() {
        return new ArrayList<>(playlists);
    }

    public Playlist createPlaylist(String name) {
        Playlist playlist = new Playlist(UUID.randomUUID().toString(), name);
        playlists.add(playlist);
        savePlaylists();
        return playlist;
    }

    public void deletePlaylist(String playlistId) {
        playlists.removeIf(p -> p.getId().equals(playlistId));
        savePlaylists();
    }

    public void renamePlaylist(String playlistId, String newName) {
        for (Playlist p : playlists) {
            if (p.getId().equals(playlistId)) {
                p.setName(newName);
                break;
            }
        }
        savePlaylists();
    }

    public void addSongToPlaylist(String playlistId, Song song) {
        for (Playlist p : playlists) {
            if (p.getId().equals(playlistId)) {
                p.addSong(song.getPath());
                break;
            }
        }
        savePlaylists();
    }

    public void removeSongFromPlaylist(String playlistId, Song song) {
        for (Playlist p : playlists) {
            if (p.getId().equals(playlistId)) {
                p.removeSong(song.getPath());
                break;
            }
        }
        savePlaylists();
    }

    public List<Song> getPlaylistSongs(String playlistId) {
        for (Playlist p : playlists) {
            if (p.getId().equals(playlistId)) {
                List<Song> result = new ArrayList<>();
                for (String path : p.getSongPaths()) {
                    Song song = songMap.get(path);
                    if (song != null) result.add(song);
                }
                return result;
            }
        }
        return new ArrayList<>();
    }

    private void loadPlaylists() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json != null) {
            Type type = new TypeToken<List<Playlist>>(){}.getType();
            List<Playlist> loaded = gson.fromJson(json, type);
            if (loaded != null) playlists.addAll(loaded);
        }
    }

    private void savePlaylists() {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply();
        notifyListeners();
    }

    /* ============================================================
       LISTENERS
       ============================================================ */

    public void addListener(Runnable listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable r : listeners) r.run();
    }

    public Playlist getPlaylist(String currentPlaylistId) {
        for (Playlist playlist : playlists) {
            if (playlist.getId().equals(currentPlaylistId)) {
                return playlist;
            }
        }
        return null;
    }
}
