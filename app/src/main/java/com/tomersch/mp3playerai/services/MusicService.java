package com.tomersch.mp3playerai.services;

import static android.os.Build.VERSION.*;
import static android.os.Build.VERSION_CODES.*;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.tomersch.mp3playerai.ai.AIRecommendationEngine;
import com.tomersch.mp3playerai.ai.AIRecommendationEngine.RecommendedSong;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.utils.ManualFileScanner;
import com.tomersch.mp3playerai.utils.UserActivityLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Enhanced Music Service with AI Continue Mode
 * <p>
 * AI Continue: Automatically adds similar songs when queue is running low
 */
public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private UserActivityLogger activityLogger;
    private Set<String> sessionPlayedPaths = new HashSet<>();
    private Set<String> sessionSkippedPaths = new HashSet<>();
    private Map<String, Integer> sessionGenreCount = new HashMap<>();
    private Map<String, Integer> sessionMoodScores = new HashMap<>();
    private List<String> recentlyAddedPaths = new ArrayList<>();
    public Song getPendingSong() {
        return pendingSong;
    }

    public void setPendingSong(Song pendingSong) {
        this.pendingSong = pendingSong;
    }

    public void setActivityLogger(UserActivityLogger activityLogger) {
        this.activityLogger = activityLogger;
    }

    // Playback modes
    public enum PlaybackMode {
        NORMAL,      // Play through queue once
        REPEAT_ALL,  // Repeat entire queue
        REPEAT_ONE,  // Repeat current song
        AI_CONTINUE  // 🧠 AI keeps adding songs!
    }

    // Media Player
    private MediaPlayer mediaPlayer;
    private List<Song> playlist;
    private int currentIndex = 0;

    // Playback control
    private PlaybackMode playbackMode = PlaybackMode.NORMAL;

    // 🧠 AI Continue Mode
    private boolean aiContinueEnabled = false;
    private int aiContinueThreshold = 5;  // Add more songs when less than 5 remaining
    private Set<String> allAvailableSongs;  // All songs in library

    // 🧠 AI Learning
    private AIRecommendationEngine aiEngine;
    private String currentSongPath;
    private long songStartTime;
    private boolean songWasCompleted = false;
    private boolean isReplaying = false;

    // Listeners
    private final List<PlaybackListener> listeners = new ArrayList<>();

    // Binder for activity communication
    private final IBinder binder = new MusicBinder();
    private boolean pendingAutostart = false;
    private Song pendingSong = null;


    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();

        mediaPlayer = new MediaPlayer();

        mediaPlayer.setAudioAttributes(
                new android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
        );

        playlist = new ArrayList<>();
        sessionPlayedPaths = new HashSet<>();
        recentlyAddedPaths = new ArrayList<>();
        allAvailableSongs = new HashSet<>();
        sessionGenreCount = new HashMap<>();
        sessionMoodScores = new HashMap<>();
        aiEngine = new AIRecommendationEngine(this,"");
        Log.d(TAG, "🧠 AI Learning initialized!");
        Log.d(TAG, aiEngine.getLearningStats());

        mediaPlayer.setOnPreparedListener(mp -> {
            try {
                if (pendingAutostart) {
                    mp.start();
                    notifyPlaybackResumed();
                    if (callback != null) callback.onPlaybackState(true);
                }
            } catch (IllegalStateException ignored) {}

            // ensure UI knows what is loaded
            Song cur = getCurrentSong();
            if (cur != null) {
                notifyNowPlaying(cur);
                if (callback != null) callback.onSongChanged(cur);
            }
        });

        setupMediaPlayerListeners();
    }

    private void setupMediaPlayerListeners() {
        // ✅ Track song completion
        mediaPlayer.setOnCompletionListener(mp -> {
            // 🧠 LEARNING: Song completed!
            songWasCompleted = true;

            if (currentSongPath != null && aiEngine != null) {
                long playDuration = System.currentTimeMillis() - songStartTime;
                long songDuration = mediaPlayer.getDuration();

                // Only count as "completed" if played at least 80%
                if (playDuration >= songDuration * 0.8) {
                    aiEngine.recordSongCompleted(currentSongPath);
                    Log.d(TAG, "🧠 Learning: Song completed - " + getCurrentSong().getTitle());
                }
            }

            // Handle playback mode
            handleCompletion();
        });

        // Error handling
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            safeSkipOnError();
            return true; // handled
        });

    }

    /**
     * Handle song completion based on playback mode
     */
    private void handleCompletion() {
        switch (playbackMode) {
            case REPEAT_ONE:
                // Replay current song
                replay();
                break;

            case REPEAT_ALL:
                if (currentIndex < playlist.size() - 1) {
                    playNext();
                } else {
                    // Loop back to start
                    currentIndex = 0;
                    playSong(playlist.get(0));
                }
                break;

            case AI_CONTINUE:
                // 🧠 AI Continue mode!
                checkAndAddAISongs();
                playNext();
                break;

            case NORMAL:
            default:
                if (currentIndex < playlist.size() - 1) {
                    playNext();
                } else {
                    Log.d(TAG, "Playlist finished");
                    notifyPlaybackEnded();
                    if (callback != null) callback.onPlaybackState(false);
                }
                break;
        }
    }
    public void notifyTheAi()
    {
        checkAndAddAISongs();
    }
    /**
     * 🧠 AI CONTINUE: Check if we need to add more songs
     */
    private void checkAndAddAISongs() {
        if (playbackMode != PlaybackMode.AI_CONTINUE) {
            return;
        }

        if (allAvailableSongs.isEmpty()) {
            Log.w(TAG, "🤖 AI Continue: No songs available in library");
            return;
        }

        int remaining = playlist.size() - currentIndex - 1;

        if (remaining > aiContinueThreshold) {
            // Still have enough songs
            return;
        }

        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "🤖 AI Continue: Only " + remaining + " songs left, adding more!");
        Log.d(TAG, "════════════════════════════════════════");

        // Analyze current session
        String topGenres = analyzeSessionGenres();
        Map<String, Integer> moodScores = analyzeSessionMoods();

        Log.d(TAG, "🤖 AI Continue: Session analysis:");
        Log.d(TAG, "   Top genres: " + topGenres);
        Log.d(TAG, "   Mood scores: " + moodScores);

        // Get recommendations (request 3x more for filtering)
        int targetCount = 10;
        Log.d(TAG, "🤖 AI Continue: Requesting " + (targetCount * 3) + " recommendations for filtering");
        Set<String> excluded = new HashSet<>();

        // current queue
                for (Song s : playlist) excluded.add(s.getPath());

        // session behavior
                excluded.addAll(sessionPlayedPaths);
                excluded.addAll(sessionSkippedPaths);

        // “do not repeat what AI just added”
                excluded.addAll(recentlyAddedPaths);

        Set<RecommendedSong> recommendations = null;
        if (SDK_INT >= N) {
            recommendations = aiEngine.getRecommendations(
                    "",0,excluded).stream().limit(targetCount * 3).collect(Collectors.toSet());
        }
        if (recommendations.isEmpty()) {
            Log.w(TAG, "🤖 AI Continue: No recommendations received from AI engine!");
            return;
        }

        Log.d(TAG, "🤖 AI Continue: Received " + recommendations.size() + " initial recommendations");

        // ═══════════════════════════════════════════════════════════════
        // BUILD FILTER SETS FOR EFFICIENT LOOKUP
        // ═══════════════════════════════════════════════════════════════

        // Set 1: Songs currently in queue
        Set<String> currentQueuePaths = new HashSet<>();
        for (Song s : playlist) {
            currentQueuePaths.add(s.getPath());
        }

        // Set 2: Songs recently added (to prevent near-duplicates)
        Set<String> recentlyAddedSet = new HashSet<>(recentlyAddedPaths);

        // Set 3: Songs user skipped in this session (they don't like them!)
        Set<String> sessionSkippedSet = new HashSet<>(sessionSkippedPaths);

        // Set 4: Songs already played in this session (avoid immediate repeats)
        Set<String> sessionPlayedSet = new HashSet<>(sessionPlayedPaths);

        Log.d(TAG, "🔍 Filter sets built:");
        Log.d(TAG, "   Current queue: " + currentQueuePaths.size() + " songs");
        Log.d(TAG, "   Recently added: " + recentlyAddedSet.size() + " songs");
        Log.d(TAG, "   Session skipped: " + sessionSkippedSet.size() + " songs");
        Log.d(TAG, "   Session played: " + sessionPlayedSet.size() + " songs");

        // ═══════════════════════════════════════════════════════════════
        // FILTER RECOMMENDATIONS (THIS IS THE FIX!)
        // ═══════════════════════════════════════════════════════════════

        List<Song> filteredSongs = new ArrayList<>();
        int duplicateCount = 0;
        int recentCount = 0;
        int skippedCount = 0;
        int alreadyPlayedCount = 0;

        for (RecommendedSong recommendedSong : recommendations) {
            Song song = recommendedSong.toSong();
            String path = song.getPath();

            // FILTER 1: Already in current queue?
            if (currentQueuePaths.contains(path)) {
                duplicateCount++;
                Log.d(TAG, "   ❌ [IN QUEUE] " + song.getTitle());
                continue;
            }

            // FILTER 2: Recently added? (prevent adding same song again too soon)
            if (recentlyAddedSet.contains(path)) {
                recentCount++;
                Log.d(TAG, "   ❌ [RECENT] " + song.getTitle());
                continue;
            }

            // FILTER 3: User skipped this song in current session?
            if (sessionSkippedSet.contains(path)) {
                skippedCount++;
                Log.d(TAG, "   ❌ [SKIPPED] " + song.getTitle());
                continue;
            }

            // FILTER 4: Already played in this session?
            if (sessionPlayedSet.contains(path)) {
                alreadyPlayedCount++;
                Log.d(TAG, "   ❌ [PLAYED] " + song.getTitle());
                continue;
            }

            // ✅ Passed all filters!
            filteredSongs.add(song);
            Log.d(TAG, "   ✅ [GOOD] " + song.getTitle());

            // Stop when we have enough
            if (filteredSongs.size() >= targetCount) {
                break;
            }
        }

        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "🔍 FILTERING COMPLETE:");
        Log.d(TAG, "   Started with: " + recommendations.size() + " recommendations");
        Log.d(TAG, "   Removed (in queue): " + duplicateCount);
        Log.d(TAG, "   Removed (recently added): " + recentCount);
        Log.d(TAG, "   Removed (user skipped): " + skippedCount);
        Log.d(TAG, "   Removed (already played): " + alreadyPlayedCount);
        Log.d(TAG, "   Final count: " + filteredSongs.size() + " songs to add");
        Log.d(TAG, "════════════════════════════════════════");

        // ═══════════════════════════════════════════════════════════════
        // FALLBACK: If all filtered out, add random songs
        // ═══════════════════════════════════════════════════════════════

        if (filteredSongs.isEmpty()) {
            Log.w(TAG, "⚠️ All recommendations were filtered out!");
            Log.w(TAG, "   Using fallback: Adding random unique songs");

            List<Song> allSongs = new ArrayList<>();
            for (String s:allAvailableSongs) {
                Song song = ManualFileScanner.createSongFromFile(new File(s));
                allSongs.add(song);
            }
            Collections.shuffle(allSongs);

            int fallbackTarget = Math.min(targetCount, 5);
            for (Song song : allSongs) {
                String path = song.getPath();

                // Only check for queue and recent duplicates in fallback
                if (!currentQueuePaths.contains(path) &&
                        !recentlyAddedSet.contains(path)) {
                    filteredSongs.add(song);
                    Log.d(TAG, "   🎲 [FALLBACK] " + song.getTitle());

                    if (filteredSongs.size() >= fallbackTarget) {
                        break;
                    }
                }
            }

            Log.d(TAG, "🎲 Fallback added: " + filteredSongs.size() + " random songs");
        }

        // ═══════════════════════════════════════════════════════════════
        // ADD FILTERED SONGS TO QUEUE
        // ═══════════════════════════════════════════════════════════════

        int addedCount = 0;
        for (Song song : filteredSongs) {
            playlist.add(song);
            recentlyAddedPaths.add(song.getPath());
            addedCount++;
        }

        // Maintain recently added list (keep last 50 songs)
        while (recentlyAddedPaths.size() > 50) {
            recentlyAddedPaths.remove(0);
        }

        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "✅ AI CONTINUE SUCCESS!");
        Log.d(TAG, "   Added " + addedCount + " new songs");
        Log.d(TAG, "   Queue size: " + playlist.size() + " songs");
        Log.d(TAG, "   Remaining: " + (playlist.size() - currentIndex - 1) + " songs");
        Log.d(TAG, "   Recently added history: " + recentlyAddedPaths.size() + " songs");
        Log.d(TAG, "════════════════════════════════════════");

        // Notify UI
        notifyPlaylistUpdated();
    }

    /**
     * 🧠 AI CONTINUE: Add AI-recommended songs to queue
     */
    private void addAISongs(int count) {
        if (aiEngine == null) {
            Log.e(TAG, "AI Engine not available");
            return;
        }

        Log.d(TAG, "🤖 AI Continue: Generating " + count + " new songs...");

        // Analyze current session to understand what user likes
        Map<String, Integer> sessionMoodPrefs = analyzeSessionMoods();
        String textQuery = analyzeSessionGenres();

        // Get AI recommendations
        List<RecommendedSong> recommendations =
                aiEngine.getRecommendations(textQuery,  count * 3,new TreeSet<>());  // Get 3x to filter

        if (recommendations.isEmpty()) {
            Log.w(TAG, "🤖 AI Continue: No recommendations available");
            return;
        }

        // Filter out already played songs (this session and recently added)
        List<Song> newSongs = new ArrayList<>();
        for (RecommendedSong rec : recommendations) {
            if (newSongs.size() >= count) break;

            // Skip if already in current playlist
            boolean alreadyInPlaylist = false;
            for (Song s : playlist) {
                if (s.getPath().equals(rec.path)) {
                    alreadyInPlaylist = true;
                    break;
                }
            }
            if (alreadyInPlaylist) continue;

            // Skip if played this session
            if (sessionPlayedPaths.contains(rec.path)) continue;

            // Skip if recently added by AI
            if (recentlyAddedPaths.contains(rec.path)) continue;

            // Add to queue!
            Song newSong = rec.toSong();
            newSongs.add(newSong);
            recentlyAddedPaths.add(rec.path);

            Log.d(TAG, "🤖 AI Continue: Adding - " + rec.title + " (score: " +
                    String.format("%.2f", rec.score) + ")");
        }

        // Add songs to playlist
        playlist.addAll(newSongs);

        // Keep recently added list manageable (last 50)
        if (recentlyAddedPaths.size() > 50) {
            recentlyAddedPaths = recentlyAddedPaths.subList(
                    recentlyAddedPaths.size() - 50,
                    recentlyAddedPaths.size()
            );
        }

        Log.d(TAG, "🤖 AI Continue: Added " + newSongs.size() + " songs! Queue now has " +
                playlist.size() + " songs");

        notifyPlaylistUpdated();
    }

    /**
     * 🧠 Analyze session moods from played songs
     */
    private Map<String, Integer> analyzeSessionMoods() {
        // Start with user's learned mood preferences
        Map<String, Integer> moods = new HashMap<>();
        moods.put("hype", 50);
        moods.put("aggressive", 50);
        moods.put("melodic", 50);
        moods.put("atmospheric", 50);
        moods.put("cinematic", 50);
        moods.put("rhythmic", 50);

        // Adjust based on session mood scores (weighted by completion)
        if (!sessionMoodScores.isEmpty()) {
            moods.putAll(sessionMoodScores);
        }

        Log.d(TAG, "🤖 AI Continue: Session moods - " + moods);
        return moods;
    }

    /**
     * 🧠 Analyze session genres from played songs
     */
    private String analyzeSessionGenres() {
        if (sessionGenreCount.isEmpty()) {
            return "";
        }

        // Find top 3 genres
        List<Map.Entry<String, Integer>> sortedGenres = new ArrayList<>(sessionGenreCount.entrySet());
        if (SDK_INT >= N) {
            sortedGenres.sort((a, b) -> b.getValue() - a.getValue());
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < Math.min(3, sortedGenres.size()); i++) {
            if (i > 0) query.append(" ");
            query.append(sortedGenres.get(i).getKey());
        }

        String result = query.toString();
        Log.d(TAG, "🤖 AI Continue: Session genres - " + result);
        return result;
    }

    /**
     * Track song metadata for AI Continue analysis
     */
    private void trackSongForAIAnalysis(Song song) {
        if (song == null) return;

        sessionPlayedPaths.add(song.getPath());

        // Track genre (would need to get from DB)
        // For now, placeholder logic
        // String genre = song.getGenre();
        // if (genre != null && !genre.isEmpty()) {
        //     sessionGenreCount.put(genre, sessionGenreCount.getOrDefault(genre, 0) + 1);
        // }
    }

    /**
     * Play a specific song
     */
    public void playSong(Song song) {
        if (song == null) {
            Log.e(TAG, "Cannot play null song");
            return;
        }

        pendingSong = song;
        pendingAutostart = true;

        // Update UI immediately (title/artist) like your old service did
        notifyNowPlaying(song);
        if (callback != null) callback.onSongChanged(song);

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(song.getPath());
            mediaPlayer.prepareAsync(); // IMPORTANT: async
        } catch (IOException e) {
            Log.e(TAG, "Error playing song", e);
            safeSkipOnError();
            return;
        }

        // 🧠 LEARNING: Track song play
        currentSongPath = song.getPath();
        songStartTime = System.currentTimeMillis();
        songWasCompleted = false;
        isReplaying = false;

        if (aiEngine != null) {
            if (SDK_INT >= N) {
                aiEngine.recordSongPlayed(song.getPath());
            }
            Log.d(TAG, "🧠 Learning: Song played - " + song.getTitle());
        }

        // 🤖 AI Continue: Track for analysis
        trackSongForAIAnalysis(song);
    }


    /**
     * Play song at specific index
     */
    public void playSongAt(int index) {
        if (playlist == null || playlist.isEmpty()) return;

        if (index < 0) index = 0;
        if (index >= playlist.size()) index = playlist.size() - 1;

        currentIndex = index;
        playSong(playlist.get(currentIndex));
    }

    /**
     * Skip to next song
     */
    public void playNext() {
        // 🧠 LEARNING: Check if current song was skipped
        trackSkipIfNeeded();

        // 🤖 AI Continue: Check if we need more songs
        if (playbackMode == PlaybackMode.AI_CONTINUE) {
            checkAndAddAISongs();
        }

        if (currentIndex < playlist.size() - 1) {
            currentIndex++;
            playSong(playlist.get(currentIndex));
        } else if (playbackMode == PlaybackMode.REPEAT_ALL || playbackMode == PlaybackMode.AI_CONTINUE) {
            // Loop back to start
            currentIndex = 0;
            playSong(playlist.get(currentIndex));
        } else {
            Log.d(TAG, "End of playlist");
            notifyPlaybackEnded();
        }
    }

    /**
     * Skip to previous song
     */
    public void playPrevious() {
        // 🧠 LEARNING: Check if current song was skipped
        trackSkipIfNeeded();

        if (currentIndex > 0) {
            currentIndex--;
            playSong(playlist.get(currentIndex));
        } else {
            // Loop to end
            currentIndex = playlist.size() - 1;
            playSong(playlist.get(currentIndex));
        }
    }

    /**
     * 🧠 Track skip event if song was skipped early
     */
    private void trackSkipIfNeeded() {
        if (currentSongPath != null && !songWasCompleted && !isReplaying) {
            long playDuration = System.currentTimeMillis() - songStartTime;
            long songDuration = mediaPlayer.getDuration();

            // Consider it a skip if played less than 30% of the song
            if (playDuration < songDuration * 0.3) {
                sessionSkippedPaths.add(currentSongPath);
                if (aiEngine != null) {
                    if (SDK_INT >= N) {
                        aiEngine.recordSongSkipped(currentSongPath);
                    }
                    Log.d(TAG, "🧠 Learning: Song skipped - " + getCurrentSong().getTitle());
                }
            }
        }
    }

    public void pause() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                notifyPlaybackPaused();
                if (callback != null) callback.onPlaybackState(false);
            }
        } catch (IllegalStateException ignored) {}
    }

    public void resume() {
        try {
            if (mediaPlayer == null) return;

            // if not prepared yet, OnPrepared will start it
            if (!mediaPlayer.isPlaying()) {
                pendingAutostart = true;
                mediaPlayer.start();
            }

            notifyPlaybackResumed();
            if (callback != null) callback.onPlaybackState(true);

        } catch (IllegalStateException e) {
            // not prepared yet
            pendingAutostart = true;
        }
    }


    /**
     * Seek to position
     */
    public void seekTo(int position) {
        mediaPlayer.seekTo(position);
    }

    /**
     * Replay current song
     */
    public void replay() {
        // 🧠 LEARNING: User wants to replay - they love it!
        if (currentSongPath != null && aiEngine != null) {
            aiEngine.recordSongReplayed(currentSongPath);
            Log.d(TAG, "🧠 Learning: Song replayed - " + getCurrentSong().getTitle());
        }

        isReplaying = true;
        mediaPlayer.seekTo(0);
        mediaPlayer.start();
    }

    /**
     * Set playlist
     */
    public void setPlaylist(List<Song> songs) {
        this.playlist = new ArrayList<>(songs);
        currentIndex = 0;
        sessionPlayedPaths.clear();
        recentlyAddedPaths.clear();
        sessionGenreCount.clear();
        sessionMoodScores.clear();

        Log.d(TAG, "Playlist set: " + songs.size() + " songs");
    }

    /**
     * 🤖 Set available songs library for AI Continue
     */
    public void setAvailableSongs(List<Song> allSongs) {
        allAvailableSongs.clear();
        for (Song song : allSongs) {
            allAvailableSongs.add(song.getPath());
        }
        Log.d(TAG, "🤖 AI Continue: Library set - " + allSongs.size() + " songs available");
    }

    // ===== PLAYBACK MODE CONTROL =====

    /**
     * Set playback mode
     */
    public void setPlaybackMode(PlaybackMode mode) {
        this.playbackMode = mode;
        this.aiContinueEnabled = (mode == PlaybackMode.AI_CONTINUE);

        Log.d(TAG, "Playback mode: " + mode);

        // Start AI Continue immediately if enabled and queue is low
        if (aiContinueEnabled) {
            checkAndAddAISongs();
        }

        notifyPlaybackModeChanged(mode);
    }

    /**
     * Get current playback mode
     */
    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    /**
     * Cycle through playback modes
     */
    public PlaybackMode cyclePlaybackMode() {
        switch (playbackMode) {
            case NORMAL:
                setPlaybackMode(PlaybackMode.REPEAT_ALL);
                break;
            case REPEAT_ALL:
                setPlaybackMode(PlaybackMode.REPEAT_ONE);
                break;
            case REPEAT_ONE:
                setPlaybackMode(PlaybackMode.AI_CONTINUE);
                break;
            case AI_CONTINUE:
                setPlaybackMode(PlaybackMode.NORMAL);
                break;
        }
        return playbackMode;
    }

    /**
     * 🤖 Set AI Continue threshold
     */
    public void setAIContinueThreshold(int threshold) {
        this.aiContinueThreshold = Math.max(1, Math.min(threshold, 20));
        Log.d(TAG, "🤖 AI Continue threshold: " + this.aiContinueThreshold);
    }

    /**
     * 🤖 Get AI Continue threshold
     */
    public int getAIContinueThreshold() {
        return aiContinueThreshold;
    }

    // ===== GETTERS =====

    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

    public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    public int getDuration() {
        return mediaPlayer.getDuration();
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public List<Song> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public String getLearningStats() {
        return aiEngine != null ? aiEngine.getLearningStats() : "Learning not initialized";
    }

    public void resetLearning() {
        if (aiEngine != null) {
            aiEngine.getLearningManager().reset();
            Log.d(TAG, "🧠 Learning data reset!");
        }
    }

    // ===== LISTENER MANAGEMENT =====

    public interface PlaybackListener {
        void onNowPlaying(Song song);
        void onPlaybackPaused();
        void onPlaybackResumed();
        void onPlaybackEnded();
        void onPlaylistUpdated();
        void onPlaybackModeChanged(PlaybackMode mode);
    }

    public void addListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void notifyNowPlaying(Song song) {
        for (PlaybackListener listener : listeners) {
            listener.onNowPlaying(song);
        }
    }

    private void notifyPlaybackPaused() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackPaused();
        }
    }

    private void notifyPlaybackResumed() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackResumed();
        }
    }

    private void notifyPlaybackEnded() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackEnded();
        }
    }

    private void notifyPlaylistUpdated() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaylistUpdated();
        }
    }

    private void notifyPlaybackModeChanged(PlaybackMode mode) {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackModeChanged(mode);
        }
    }

    // ===== SERVICE LIFECYCLE =====

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (aiEngine != null) {
            aiEngine.close();
        }

        Log.d(TAG, "MusicService destroyed");
    }
    // === Backward compatible callback for MainActivity ===
    public interface Callback {
        void onSongChanged(Song song);
        void onPlaybackState(boolean playing);
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;

        // Sync immediately if already have state
        Song current = getCurrentSong();
        if (current != null && this.callback != null) {
            this.callback.onSongChanged(current);
            this.callback.onPlaybackState(isPlaying());
        }
    }
// === Backward compatible API expected by MainActivity ===

    public void setQueue(List<Song> songs, int startIndex) {
        setPlaylist(songs);
        playAt(startIndex);
    }

    public void playAt(int index) {
        playSongAt(index);
    }

    public void next() {
        playNext();
    }

    public void previous() {
        playPrevious();
    }

    public List<Song> getQueue() {
        return getPlaylist();
    }
    private void safeSkipOnError() {
        try {
            // mark as not completed
            songWasCompleted = false;
        } catch (Exception ignored) {}

        if (playlist == null || playlist.isEmpty()) {
            notifyPlaybackEnded();
            if (callback != null) callback.onPlaybackState(false);
            return;
        }

        int next = Math.min(currentIndex + 1, playlist.size() - 1);
        if (next == currentIndex && playlist.size() > 1) next = (currentIndex + 1) % playlist.size();

        currentIndex = next;
        playSong(playlist.get(currentIndex));
    }


}