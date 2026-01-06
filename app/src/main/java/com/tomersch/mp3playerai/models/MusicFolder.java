package com.tomersch.mp3playerai.models;

public class MusicFolder {
    private String folderName;
    private String folderPath;
    private int songCount;

    public MusicFolder(String folderName, String folderPath, int songCount) {
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.songCount = songCount;
    }

    public String getFolderName() { return folderName; }
    public String getFolderPath() { return folderPath; }
    public int getSongCount() { return songCount; }
}