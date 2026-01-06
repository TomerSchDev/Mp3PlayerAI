package com.tomersch.mp3playerai.models;

import java.io.Serializable;

public class Song implements Serializable {
    private String title;
    private String artist;
    private String path;
    private long duration;
    // Add these fields to your Song.java
    private int aggressive;
    private int melodic;
    private int atmospheric;
    private int cinematic;
    private int rhythmic;

// Update your constructor and add getters/setters for these 5 fields

    public Song(String title, String artist, String path, long duration) {
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}