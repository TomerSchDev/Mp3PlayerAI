package com.tomersch.mp3playerai.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.tomersch.mp3playerai.activities.MainActivity;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MusicService owns playback + queue. UI controls it via binder calls.
 * Foreground notification provides playback controls.
 */
public class MusicService extends Service
        implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    private static final String TAG = "MusicService";

    // Notification
    private static final String CHANNEL_ID = "music";
    private static final int NOTIFICATION_ID = 1;

    // Actions (notification -> service)
    public static final String ACTION_PLAY_PAUSE = "com.tomersch.mp3playerai.action.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.tomersch.mp3playerai.action.NEXT";
    public static final String ACTION_PREVIOUS = "com.tomersch.mp3playerai.action.PREVIOUS";
    public static final String ACTION_STOP = "com.tomersch.mp3playerai.action.STOP";

    public interface Callback {
        void onSongChanged(Song song);
        void onPlaybackState(boolean playing);
    }

    private final IBinder binder = new MusicBinder();

    private MediaPlayer player;
    private Callback callback;

    private final List<Song> queue = new ArrayList<>();
    private int index = -1;

    // Optional features
    private boolean shuffle = false;
    private int repeatMode = 0; // 0 none, 1 repeat all, 2 repeat one

    private UserActivityLogger activityLogger;

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        player = new MediaPlayer();
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
        }

        createChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Handle notification actions.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE:
                    if (isPlaying()) pause();
                    else resume();
                    break;
                case ACTION_NEXT:
                    next();
                    break;
                case ACTION_PREVIOUS:
                    previous();
                    break;
                case ACTION_STOP:
                    stopPlaybackAndService();
                    break;
            }
        }
        return START_STICKY;
    }

    /* ============================================================
       Public API used by MainActivity
       ============================================================ */

    public void setCallback(Callback callback) {
        this.callback = callback;

        // Immediately sync UI if we already have state
        Song current = getCurrentSong();
        if (current != null && this.callback != null) {
            this.callback.onSongChanged(current);
            this.callback.onPlaybackState(isPlaying());
        }
    }

    public void setActivityLogger(UserActivityLogger logger) {
        this.activityLogger = logger;
    }

    public void setQueue(List<Song> songs, int startIndex) {
        queue.clear();
        if (songs != null) queue.addAll(songs);

        if (shuffle) {
            // keep selected song at requested index but shuffle the rest
            Song startSong = null;
            if (startIndex >= 0 && startIndex < queue.size()) startSong = queue.get(startIndex);
            Collections.shuffle(queue);
            if (startSong != null) {
                int newIdx = queue.indexOf(startSong);
                if (newIdx >= 0) startIndex = newIdx;
            }
        }

        playAt(startIndex);
    }

    public void playAt(int pos) {
        if (queue.isEmpty()) return;

        if (pos < 0) pos = 0;
        if (pos >= queue.size()) pos = queue.size() - 1;

        index = pos;
        Song song = queue.get(index);

        // Log session start (optional)
        if (activityLogger != null) {
            activityLogger.startSongSession(song);
        }

        try {
            player.reset();
            player.setDataSource(song.getPath());
            player.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "setDataSource/prepareAsync failed: " + e.getMessage(), e);
            // Try skipping to next if broken file
            safeSkipOnError();
            return;
        }

        // Update UI immediately (title/artist) even before prepared
        if (callback != null) callback.onSongChanged(song);

        // Ensure we are foreground with correct controls
        startForeground(NOTIFICATION_ID, buildNotification(song, false));
    }

    public void pause() {
        if (player == null) return;
        if (!player.isPlaying()) return;

        player.pause();
        if (callback != null) callback.onPlaybackState(false);

        Song song = getCurrentSong();
        if (song != null) {
            // Update notification to show play icon
            updateNotification(song, false);
        }
    }

    public void resume() {
        if (player == null) return;
        if (queue.isEmpty() || index < 0) return;

        // If not prepared yet, do nothing; onPrepared will start it.
        try {
            if (!player.isPlaying()) player.start();
        } catch (IllegalStateException ignored) {
            // player might not be prepared yet
        }

        if (callback != null) callback.onPlaybackState(true);

        Song song = getCurrentSong();
        if (song != null) {
            updateNotification(song, true);
        }
    }

    public void next() {
        if (queue.isEmpty()) return;

        if (repeatMode == 2) {
            // repeat one
            playAt(index);
            return;
        }

        int next = index + 1;
        if (next >= queue.size()) {
            if (repeatMode == 1) next = 0;      // repeat all
            else {
                // end playback
                stopPlaybackAndService();
                return;
            }
        }
        playAt(next);
    }

    public void previous() {
        if (queue.isEmpty()) return;

        if (repeatMode == 2) {
            playAt(index);
            return;
        }

        int prev = index - 1;
        if (prev < 0) prev = (repeatMode == 1) ? queue.size() - 1 : 0;
        playAt(prev);
    }

    public List<Song> getQueue() {
        return new ArrayList<>(queue);
    }

    public int getCurrentIndex() {
        return index;
    }

    public Song getCurrentSong() {
        if (index >= 0 && index < queue.size()) return queue.get(index);
        return null;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    // Optional toggles if you want later
    public void setShuffle(boolean enabled) { shuffle = enabled; }
    public boolean isShuffle() { return shuffle; }

    public void setRepeatMode(int mode) { repeatMode = mode; } // 0/1/2
    public int getRepeatMode() { return repeatMode; }

    /* ============================================================
       MediaPlayer callbacks
       ============================================================ */

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
        if (callback != null) callback.onPlaybackState(true);

        Song song = getCurrentSong();
        if (song != null) {
            updateNotification(song, true);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        // Log end (completed)
        if (activityLogger != null) {
            activityLogger.endSongSession(true);
        }
        next();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
        safeSkipOnError();
        return true; // handled
    }

    private void safeSkipOnError() {
        // Log end (not completed)
        if (activityLogger != null) {
            activityLogger.endSongSession(false);
        }
        // Skip to next track
        if (!queue.isEmpty()) {
            int next = Math.min(index + 1, queue.size() - 1);
            if (next == index && queue.size() > 1) next = (index + 1) % queue.size();
            playAt(next);
        } else {
            stopPlaybackAndService();
        }
    }

    /* ============================================================
       Notification
       ============================================================ */

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID,
                    "Music",
                    NotificationManager.IMPORTANCE_LOW
            );
            c.setDescription("Music playback controls");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private void updateNotification(Song song, boolean playing) {
        Notification n = buildNotification(song, playing);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, n);
    }

    private Notification buildNotification(Song song, boolean playing) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                100,
                new Intent(this, MainActivity.class),
                pendingIntentFlags()
        );

        PendingIntent prevIntent = PendingIntent.getService(
                this,
                101,
                new Intent(this, MusicService.class).setAction(ACTION_PREVIOUS),
                pendingIntentFlags()
        );

        PendingIntent playPauseIntent = PendingIntent.getService(
                this,
                102,
                new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE),
                pendingIntentFlags()
        );

        PendingIntent nextIntent = PendingIntent.getService(
                this,
                103,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT),
                pendingIntentFlags()
        );

        int playPauseIcon = playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String playPauseTitle = playing ? "Pause" : "Play";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play) // you can replace with your app icon
                .setContentTitle(song.getTitle() != null ? song.getTitle() : "Unknown title")
                .setContentText(song.getArtist() != null ? song.getArtist() : "Unknown artist")
                .setContentIntent(contentIntent)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
                .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void stopPlaybackAndService() {
        try {
            if (player != null) {
                player.stop();
            }
        } catch (IllegalStateException ignored) {}

        if (callback != null) callback.onPlaybackState(false);

        stopForeground(true);
        stopSelf();
    }

    /* ============================================================
       Cleanup
       ============================================================ */

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (player != null) {
                player.release();
                player = null;
            }
        } catch (Exception ignored) {}
        callback = null;
        queue.clear();
        index = -1;
    }
}
