package com.tomersch.mp3playerai.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user-created playlist
 */
public class Playlist implements Serializable {
    private String id;
    private String name;
    private List<String> songPaths; // Paths to songs in this playlist
    private long createdAt;
    private long modifiedAt;

    public Playlist(String id, String name) {
        this.id = id;
        this.name = name;
        this.songPaths = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = System.currentTimeMillis();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.modifiedAt = System.currentTimeMillis();
    }

    public List<String> getSongPaths() {
        return songPaths;
    }

    public void setSongPaths(List<String> songPaths) {
        this.songPaths = songPaths;
        this.modifiedAt = System.currentTimeMillis();
    }

    public void addSong(String songPath) {
        if (!songPaths.contains(songPath)) {
            songPaths.add(songPath);
            this.modifiedAt = System.currentTimeMillis();
        }
    }

    public void removeSong(String songPath) {
        songPaths.remove(songPath);
        this.modifiedAt = System.currentTimeMillis();
    }

    public boolean containsSong(String songPath) {
        return songPaths.contains(songPath);
    }

    public int getSongCount() {
        return songPaths.size();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }
}
