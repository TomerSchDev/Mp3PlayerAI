package com.tomersch.mp3playerai.utils;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.Log;

import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manual file scanner that searches file system directly
 * instead of relying on MediaStore
 */
public class ManualFileScanner {

    private static final String TAG = "ManualFileScanner";

    /**
     * Scan device storage for MP3 files
     * @return List of Song objects found
     */
    public static List<Song> scanForAudioFiles(Context context) {
        List<Song> songs = new ArrayList<>();

        Log.d(TAG, "========== Starting Manual File Scan ==========");

        // Directories to scan
        List<File> dirsToScan = new ArrayList<>();

        // Add common music directories
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        // Get external storage root
        File externalStorage = Environment.getExternalStorageDirectory();

        Log.d(TAG, "External storage root: " + externalStorage.getAbsolutePath());
        Log.d(TAG, "Music directory: " + musicDir.getAbsolutePath());
        Log.d(TAG, "Download directory: " + downloadDir.getAbsolutePath());

        dirsToScan.add(musicDir);
        dirsToScan.add(downloadDir);
        dirsToScan.add(dcimDir);
        dirsToScan.add(documentsDir);
        dirsToScan.add(externalStorage); // Scan root as well

        // Also check for common folder names
        File[] commonFolders = {
                new File(externalStorage, "Music"),
                new File(externalStorage, "music"),
                new File(externalStorage, "Download"),
                new File(externalStorage, "download"),
                new File(externalStorage, "Audio"),
                new File(externalStorage, "audio"),
                new File(externalStorage, "Songs"),
                new File(externalStorage, "songs")
        };

        for (File folder : commonFolders) {
            if (folder.exists() && !dirsToScan.contains(folder)) {
                dirsToScan.add(folder);
            }
        }

        // Scan each directory
        for (File dir : dirsToScan) {
            if (dir.exists() && dir.isDirectory() && dir.canRead()) {
                Log.d(TAG, "Scanning directory: " + dir.getAbsolutePath());
                scanDirectory(dir, songs, 0, 3); // Max depth of 3 levels
            } else {
                Log.d(TAG, "Skipping directory (doesn't exist or can't read): " + dir.getAbsolutePath());
            }
        }

        Log.d(TAG, "Total audio files found: " + songs.size());
        Log.d(TAG, "========== Manual File Scan Complete ==========");

        return songs;
    }

    /**
     * Recursively scan a directory for audio files
     */
    private static void scanDirectory(File directory, List<Song> songs, int depth, int maxDepth) {
        // Prevent infinite recursion
        if (depth > maxDepth) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                // Skip hidden files and system directories
                if (file.getName().startsWith(".")) {
                    continue;
                }

                // Skip Android system directories
                String fileName = file.getName().toLowerCase();
                if (fileName.equals("android") || fileName.equals("data")) {
                    continue;
                }

                if (file.isDirectory()) {
                    // Recursively scan subdirectory
                    scanDirectory(file, songs, depth + 1, maxDepth);
                } else if (file.isFile() && isAudioFile(file)) {
                    // Found an audio file
                    Song song = createSongFromFile(file);
                    if (song != null) {
                        songs.add(song);
                        Log.d(TAG, "  Found: " + song.getTitle() + " at " + file.getAbsolutePath());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error scanning file: " + file.getAbsolutePath() + " - " + e.getMessage());
            }
        }
    }

    /**
     * Check if file is an audio file based on extension
     */
    private static boolean isAudioFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") ||
                name.endsWith(".m4a") ||
                name.endsWith(".wav") ||
                name.endsWith(".ogg") ||
                name.endsWith(".flac") ||
                name.endsWith(".aac") ||
                name.endsWith(".wma");
    }

    /**
     * Create a Song object from a file
     */
    public static Song createSongFromFile(File file) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());

            // Try to get metadata
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

            // Use filename as title if no metadata
            if (title == null || title.trim().isEmpty()) {
                title = file.getName().replaceFirst("[.][^.]+$", ""); // Remove extension
            }

            if (artist == null || artist.trim().isEmpty()) {
                artist = "Unknown Artist";
            }

            long duration = 0;
            if (durationStr != null) {
                try {
                    duration = Long.parseLong(durationStr);
                } catch (NumberFormatException e) {
                    duration = 0;
                }
            }

            retriever.release();

            return new Song(title, artist, file.getAbsolutePath(), duration);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting metadata from: " + file.getAbsolutePath() + " - " + e.getMessage());

            // Fallback: create song with basic info
            String title = file.getName().replaceFirst("[.][^.]+$", "");
            return new Song(title, "Unknown Artist", file.getAbsolutePath(), 0);
        }
    }
}