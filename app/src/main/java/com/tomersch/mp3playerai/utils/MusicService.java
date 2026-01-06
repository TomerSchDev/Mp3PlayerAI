package com.tomersch.mp3playerai.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.tomersch.mp3playerai.activities.MainActivity;
import com.tomersch.mp3playerai.models.Song;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    public static final String ACTION_PLAY = "com.tomersch.mp3playerai.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.tomersch.mp3playerai.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.tomersch.mp3playerai.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.tomersch.mp3playerai.ACTION_NEXT";

    private static final String CHANNEL_ID = "MusicPlayerChannel";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new MusicBinder();
    private MediaPlayer mediaPlayer;
    private List<Song> songList;
    private int currentSongIndex = -1;
    private OnSongChangeListener listener;
    private UserActivityLogger activityLogger;
    private int lastReportedPosition = 0;

    public interface OnSongChangeListener {
        void onSongChanged(Song song);
        void onPlaybackStateChanged(boolean isPlaying);
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);

        songList = new ArrayList<>();
        createNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    if (!isPlaying()) {
                        resumeSong();
                    }
                    break;
                case ACTION_PAUSE:
                    pauseSong();
                    break;
                case ACTION_PREVIOUS:
                    playPrevious();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
            }
        }
        return START_STICKY;
    }

    public void setSongList(List<Song> songs) {
        this.songList = songs;
    }

    public void setOnSongChangeListener(OnSongChangeListener listener) {
        this.listener = listener;
    }

    public void setActivityLogger(UserActivityLogger logger) {
        this.activityLogger = logger;
    }

    public void playSong(int position) {
        if (songList == null || songList.isEmpty() || position < 0 || position >= songList.size()) {
            return;
        }

        currentSongIndex = position;
        Song song = songList.get(position);

        // Log song start
        if (activityLogger != null) {
            activityLogger.startSongSession(song);
        }

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(song.getPath());
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e("MusicService", "Error playing song: " + e.getMessage());
        }

        if (listener != null) {
            listener.onSongChanged(song);
        }

        showNotification();
    }

    public void pauseSong() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();

            // Log pause
            if (activityLogger != null && getCurrentSong() != null) {
                activityLogger.pauseSong(getCurrentSong(), getCurrentPosition());
            }

            if (listener != null) {
                listener.onPlaybackStateChanged(false);
            }
            showNotification();
        }
    }

    public void resumeSong() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();

            // Log resume
            if (activityLogger != null && getCurrentSong() != null) {
                activityLogger.resumeSong(getCurrentSong(), getCurrentPosition());
            }

            if (listener != null) {
                listener.onPlaybackStateChanged(true);
            }
            showNotification();
        }
    }

    public void playNext() {
        if (songList != null && !songList.isEmpty()) {
            Song fromSong = getCurrentSong();

            // End current session (skipped)
            if (activityLogger != null) {
                activityLogger.endSongSession(false);
            }

            currentSongIndex = (currentSongIndex + 1) % songList.size();
            Song toSong = getCurrentSong();

            // Log next pressed
            if (activityLogger != null) {
                activityLogger.logNextPressed(fromSong, toSong);
            }

            playSong(currentSongIndex);
        }
    }

    public void playPrevious() {
        if (songList != null && !songList.isEmpty()) {
            Song fromSong = getCurrentSong();

            // End current session (skipped)
            if (activityLogger != null) {
                activityLogger.endSongSession(false);
            }

            currentSongIndex = (currentSongIndex - 1 + songList.size()) % songList.size();
            Song toSong = getCurrentSong();

            // Log previous pressed
            if (activityLogger != null) {
                activityLogger.logPreviousPressed(fromSong, toSong);
            }

            playSong(currentSongIndex);
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            int oldPosition = mediaPlayer.getCurrentPosition();
            mediaPlayer.seekTo(position);

            // Log seek
            if (activityLogger != null && getCurrentSong() != null) {
                activityLogger.seekInSong(getCurrentSong(), oldPosition, position);
            }
        }
    }

    public Song getCurrentSong() {
        if (songList != null && currentSongIndex >= 0 && currentSongIndex < songList.size()) {
            return songList.get(currentSongIndex);
        }
        return null;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        // Log song completed
        if (activityLogger != null) {
            activityLogger.endSongSession(true);
        }

        playNext();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
        if (listener != null) {
            listener.onPlaybackStateChanged(true);
        }
        showNotification();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.reset();
        return false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music playback controls");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification() {
        Song currentSong = getCurrentSong();
        if (currentSong == null) {
            return;
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Intent previousIntent = new Intent(this, MusicService.class);
        previousIntent.setAction(ACTION_PREVIOUS);
        PendingIntent previousPendingIntent = PendingIntent.getService(
                this, 0, previousIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Intent playPauseIntent = new Intent(this, MusicService.class);
        playPauseIntent.setAction(isPlaying() ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Intent nextIntent = new Intent(this, MusicService.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentSong.getTitle())
                .setContentText(currentSong.getArtist())
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_media_previous, "Previous", previousPendingIntent)
                .addAction(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying() ? "Pause" : "Play", playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying())
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopForeground(true);
    }
}