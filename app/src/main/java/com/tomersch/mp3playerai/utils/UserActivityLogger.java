package com.tomersch.mp3playerai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tomersch.mp3playerai.models.Song;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Comprehensive logging system for user actions and listening behavior
 * Tracks what users listen to, for how long, skips, pauses, etc.
 */
public class UserActivityLogger {

    private static final String TAG = "UserActivityLogger";
    private static final String PREFS_NAME = "UserActivityLogs";
    private static final String KEY_LOGS = "activity_logs";
    private static final int MAX_LOGS = 1000; // Keep last 1000 logs

    private Context context;
    private SharedPreferences prefs;
    private Gson gson;
    private SimpleDateFormat dateFormat;

    // Current listening session tracking
    private String currentSongPath;
    private long sessionStartTime;
    private long totalListenTime;
    private int pauseCount;
    private boolean wasPaused;

    public UserActivityLogger(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    // ==================== SESSION TRACKING ====================

    /**
     * Start tracking a new song session
     */
    public void startSongSession(Song song) {
        // End previous session if exists
        if (currentSongPath != null) {
            endSongSession(false);
        }

        currentSongPath = song.getPath();
        sessionStartTime = System.currentTimeMillis();
        totalListenTime = 0;
        pauseCount = 0;
        wasPaused = false;

        logEvent(new ActivityLog(
                ActivityLog.EventType.SONG_STARTED,
                song.getTitle(),
                song.getArtist(),
                song.getPath(),
                0,
                null
        ));

        Log.d(TAG, "Started session: " + song.getTitle());
    }

    /**
     * Pause current song
     */
    public void pauseSong(Song song, int currentPosition) {
        if (currentSongPath == null) return;

        wasPaused = true;
        pauseCount++;

        // Calculate listen time since last resume
        long listenDuration = System.currentTimeMillis() - sessionStartTime;
        totalListenTime += listenDuration;

        logEvent(new ActivityLog(
                ActivityLog.EventType.SONG_PAUSED,
                song.getTitle(),
                song.getArtist(),
                song.getPath(),
                currentPosition,
                "Pause count: " + pauseCount + ", Total listen time: " + formatDuration(totalListenTime)
        ));

        Log.d(TAG, "Paused: " + song.getTitle() + " at " + currentPosition + "ms");
    }

    /**
     * Resume current song
     */
    public void resumeSong(Song song, int currentPosition) {
        if (currentSongPath == null) return;

        sessionStartTime = System.currentTimeMillis(); // Reset start time

        logEvent(new ActivityLog(
                ActivityLog.EventType.SONG_RESUMED,
                song.getTitle(),
                song.getArtist(),
                song.getPath(),
                currentPosition,
                null
        ));

        Log.d(TAG, "Resumed: " + song.getTitle() + " from " + currentPosition + "ms");
    }

    /**
     * User seeked in song
     */
    public void seekInSong(Song song, int fromPosition, int toPosition) {
        if (currentSongPath == null) return;

        logEvent(new ActivityLog(
                ActivityLog.EventType.SONG_SEEKED,
                song.getTitle(),
                song.getArtist(),
                song.getPath(),
                toPosition,
                "From: " + fromPosition + "ms to: " + toPosition + "ms"
        ));

        Log.d(TAG, "Seeked in: " + song.getTitle() + " from " + fromPosition + " to " + toPosition);
    }

    /**
     * End current song session
     * @param completed True if song played to completion, false if skipped
     */
    public void endSongSession(boolean completed) {
        if (currentSongPath == null) return;

        // Calculate final listen time
        if (!wasPaused) {
            long listenDuration = System.currentTimeMillis() - sessionStartTime;
            totalListenTime += listenDuration;
        }

        ActivityLog.EventType eventType = completed ?
                ActivityLog.EventType.SONG_COMPLETED :
                ActivityLog.EventType.SONG_SKIPPED;

        logEvent(new ActivityLog(
                eventType,
                "Previous Song",
                "",
                currentSongPath,
                0,
                "Total listen time: " + formatDuration(totalListenTime) +
                        ", Pause count: " + pauseCount +
                        ", Completed: " + completed
        ));

        Log.d(TAG, (completed ? "Completed" : "Skipped") + " song. Listen time: " +
                formatDuration(totalListenTime));

        // Reset session
        currentSongPath = null;
        sessionStartTime = 0;
        totalListenTime = 0;
        pauseCount = 0;
        wasPaused = false;
    }

    // ==================== USER ACTION LOGGING ====================

    /**
     * Log next button press
     */
    public void logNextPressed(Song fromSong, Song toSong) {
        logEvent(new ActivityLog(
                ActivityLog.EventType.NEXT_PRESSED,
                fromSong != null ? fromSong.getTitle() : "None",
                fromSong != null ? fromSong.getArtist() : "",
                fromSong != null ? fromSong.getPath() : "",
                0,
                "Next song: " + (toSong != null ? toSong.getTitle() : "None")
        ));
    }

    /**
     * Log previous button press
     */
    public void logPreviousPressed(Song fromSong, Song toSong) {
        logEvent(new ActivityLog(
                ActivityLog.EventType.PREVIOUS_PRESSED,
                fromSong != null ? fromSong.getTitle() : "None",
                fromSong != null ? fromSong.getArtist() : "",
                fromSong != null ? fromSong.getPath() : "",
                0,
                "Previous song: " + (toSong != null ? toSong.getTitle() : "None")
        ));
    }

    /**
     * Log song selection from list
     */
    public void logSongSelected(Song song, int position) {
        logEvent(new ActivityLog(
                ActivityLog.EventType.SONG_SELECTED,
                song.getTitle(),
                song.getArtist(),
                song.getPath(),
                0,
                "Position in list: " + position
        ));
    }

    /**
     * Log app open
     */
    public void logAppOpened() {
        logEvent(new ActivityLog(
                ActivityLog.EventType.APP_OPENED,
                "",
                "",
                "",
                0,
                null
        ));
    }

    /**
     * Log app close
     */
    public void logAppClosed() {
        endSongSession(false); // End any active session

        logEvent(new ActivityLog(
                ActivityLog.EventType.APP_CLOSED,
                "",
                "",
                "",
                0,
                null
        ));
    }

    /**
     * Log library scan
     */
    public void logLibraryScan(int songsFound, int added, int removed) {
        logEvent(new ActivityLog(
                ActivityLog.EventType.LIBRARY_SCANNED,
                "",
                "",
                "",
                0,
                "Total: " + songsFound + ", Added: " + added + ", Removed: " + removed
        ));
    }

    // ==================== LOG MANAGEMENT ====================

    /**
     * Log an event
     */
    private void logEvent(ActivityLog activityLog) {
        List<ActivityLog> logs = getAllLogs();
        logs.add(activityLog);

        // Keep only last MAX_LOGS
        if (logs.size() > MAX_LOGS) {
            logs = logs.subList(logs.size() - MAX_LOGS, logs.size());
        }

        saveLogs(logs);

        // Also log to Logcat for debugging
        Log.i(TAG, "EVENT: " + activityLog.toString());
    }

    /**
     * Get all logs
     */
    public List<ActivityLog> getAllLogs() {
        String json = prefs.getString(KEY_LOGS, null);
        if (json == null) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<ActivityLog>>(){}.getType();
        List<ActivityLog> logs = gson.fromJson(json, type);
        return logs != null ? logs : new ArrayList<>();
    }

    /**
     * Get logs for specific song
     */
    public List<ActivityLog> getLogsForSong(String songPath) {
        List<ActivityLog> allLogs = getAllLogs();
        List<ActivityLog> songLogs = new ArrayList<>();

        for (ActivityLog log : allLogs) {
            if (log.getSongPath().equals(songPath)) {
                songLogs.add(log);
            }
        }

        return songLogs;
    }

    /**
     * Get logs by event type
     */
    public List<ActivityLog> getLogsByType(ActivityLog.EventType eventType) {
        List<ActivityLog> allLogs = getAllLogs();
        List<ActivityLog> typeLogs = new ArrayList<>();

        for (ActivityLog log : allLogs) {
            if (log.getEventType() == eventType) {
                typeLogs.add(log);
            }
        }

        return typeLogs;
    }

    /**
     * Get logs from specific time range
     */
    public List<ActivityLog> getLogsByTimeRange(long startTime, long endTime) {
        List<ActivityLog> allLogs = getAllLogs();
        List<ActivityLog> rangeLogs = new ArrayList<>();

        for (ActivityLog log : allLogs) {
            if (log.getTimestamp() >= startTime && log.getTimestamp() <= endTime) {
                rangeLogs.add(log);
            }
        }

        return rangeLogs;
    }

    /**
     * Clear all logs
     */
    public void clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply();
        Log.d(TAG, "All logs cleared");
    }

    /**
     * Export logs as JSON string
     */
    public String exportLogsAsJson() {
        List<ActivityLog> logs = getAllLogs();
        return gson.toJson(logs);
    }

    /**
     * Export logs as CSV string
     */
    public String exportLogsAsCsv() {
        List<ActivityLog> logs = getAllLogs();
        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("Timestamp,Event Type,Song Title,Artist,Song Path,Position (ms),Additional Info\n");

        // Data
        for (ActivityLog log : logs) {
            csv.append(log.toCsvRow()).append("\n");
        }

        return csv.toString();
    }

    /**
     * Get total listening statistics
     */
    public ListeningStats getListeningStats() {
        List<ActivityLog> logs = getAllLogs();

        int totalSongsPlayed = 0;
        int totalSongsCompleted = 0;
        int totalSongsSkipped = 0;
        int totalPauses = 0;
        int totalSeeks = 0;
        long totalListenTime = 0;

        for (ActivityLog log : logs) {
            switch (log.getEventType()) {
                case SONG_STARTED:
                    totalSongsPlayed++;
                    break;
                case SONG_COMPLETED:
                    totalSongsCompleted++;
                    break;
                case SONG_SKIPPED:
                    totalSongsSkipped++;
                    break;
                case SONG_PAUSED:
                    totalPauses++;
                    break;
                case SONG_SEEKED:
                    totalSeeks++;
                    break;
            }

            // Extract listen time from additional info if available
            if (log.getAdditionalInfo() != null &&
                    log.getAdditionalInfo().contains("Total listen time:")) {
                try {
                    String timeStr = log.getAdditionalInfo()
                            .split("Total listen time:")[1]
                            .split(",")[0]
                            .trim();
                    // Parse time back to milliseconds (simplified)
                    totalListenTime += parseDuration(timeStr);
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
        }

        return new ListeningStats(
                totalSongsPlayed,
                totalSongsCompleted,
                totalSongsSkipped,
                totalPauses,
                totalSeeks,
                totalListenTime
        );
    }

    /**
     * Save logs to SharedPreferences
     */
    private void saveLogs(List<ActivityLog> logs) {
        String json = gson.toJson(logs);
        prefs.edit().putString(KEY_LOGS, json).apply();
    }

    /**
     * Format duration in milliseconds to readable string
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }

    /**
     * Parse duration string back to milliseconds (simplified)
     */
    private long parseDuration(String duration) {
        try {
            String[] parts = duration.split("m");
            long minutes = Long.parseLong(parts[0].trim());
            long seconds = Long.parseLong(parts[1].replace("s", "").trim());
            return (minutes * 60 + seconds) * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== DATA CLASSES ====================

    /**
     * Activity log entry
     */
    public static class ActivityLog {
        public enum EventType {
            SONG_STARTED,
            SONG_PAUSED,
            SONG_RESUMED,
            SONG_COMPLETED,
            SONG_SKIPPED,
            SONG_SEEKED,
            SONG_SELECTED,
            NEXT_PRESSED,
            PREVIOUS_PRESSED,
            APP_OPENED,
            APP_CLOSED,
            LIBRARY_SCANNED
        }

        private long timestamp;
        private String formattedTime;
        private EventType eventType;
        private String songTitle;
        private String artist;
        private String songPath;
        private int positionMs;
        private String additionalInfo;

        public ActivityLog(EventType eventType, String songTitle, String artist,
                           String songPath, int positionMs, String additionalInfo) {
            this.timestamp = System.currentTimeMillis();
            this.formattedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()).format(new Date(timestamp));
            this.eventType = eventType;
            this.songTitle = songTitle;
            this.artist = artist;
            this.songPath = songPath;
            this.positionMs = positionMs;
            this.additionalInfo = additionalInfo;
        }

        // Getters
        public long getTimestamp() { return timestamp; }
        public String getFormattedTime() { return formattedTime; }
        public EventType getEventType() { return eventType; }
        public String getSongTitle() { return songTitle; }
        public String getArtist() { return artist; }
        public String getSongPath() { return songPath; }
        public int getPositionMs() { return positionMs; }
        public String getAdditionalInfo() { return additionalInfo; }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT,"[%s] %s - %s (%s) @ %dms - %s",
                    formattedTime, eventType, songTitle, artist, positionMs,
                    additionalInfo != null ? additionalInfo : "");
        }

        public String toCsvRow() {
            return String.format(Locale.ROOT,"%s,%s,%s,%s,%s,%d,%s",
                    formattedTime,
                    eventType,
                    escapeCsv(songTitle),
                    escapeCsv(artist),
                    escapeCsv(songPath),
                    positionMs,
                    escapeCsv(additionalInfo != null ? additionalInfo : ""));
        }

        private String escapeCsv(String value) {
            if (value.contains(",") || value.contains("\"")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }
    }

    /**
     * Listening statistics summary
     */
    public static class ListeningStats {
        private int totalSongsPlayed;
        private int totalSongsCompleted;
        private int totalSongsSkipped;
        private int totalPauses;
        private int totalSeeks;
        private long totalListenTimeMs;

        public ListeningStats(int totalSongsPlayed, int totalSongsCompleted,
                              int totalSongsSkipped, int totalPauses,
                              int totalSeeks, long totalListenTimeMs) {
            this.totalSongsPlayed = totalSongsPlayed;
            this.totalSongsCompleted = totalSongsCompleted;
            this.totalSongsSkipped = totalSongsSkipped;
            this.totalPauses = totalPauses;
            this.totalSeeks = totalSeeks;
            this.totalListenTimeMs = totalListenTimeMs;
        }

        public int getTotalSongsPlayed() { return totalSongsPlayed; }
        public int getTotalSongsCompleted() { return totalSongsCompleted; }
        public int getTotalSongsSkipped() { return totalSongsSkipped; }
        public int getTotalPauses() { return totalPauses; }
        public int getTotalSeeks() { return totalSeeks; }
        public long getTotalListenTimeMs() { return totalListenTimeMs; }

        public double getCompletionRate() {
            if (totalSongsPlayed == 0) return 0;
            return (double) totalSongsCompleted / totalSongsPlayed * 100;
        }

        public double getSkipRate() {
            if (totalSongsPlayed == 0) return 0;
            return (double) totalSongsSkipped / totalSongsPlayed * 100;
        }

        @Override
        public String toString() {
            return String.format(
                    "Songs Played: %d\n" +
                            "Completed: %d (%.1f%%)\n" +
                            "Skipped: %d (%.1f%%)\n" +
                            "Total Pauses: %d\n" +
                            "Total Seeks: %d\n" +
                            "Total Listen Time: %s",
                    totalSongsPlayed,
                    totalSongsCompleted, getCompletionRate(),
                    totalSongsSkipped, getSkipRate(),
                    totalPauses,
                    totalSeeks,
                    formatTime(totalListenTimeMs)
            );
        }

        private String formatTime(long millis) {
            long seconds = millis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%dh %dm", hours, minutes);
        }
    }
}