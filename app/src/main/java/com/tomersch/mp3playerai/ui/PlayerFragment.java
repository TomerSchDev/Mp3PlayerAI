package com.tomersch.mp3playerai.ui;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.services.MusicService;

import java.util.Locale;

/**
 * Enhanced Player Fragment with AI Continue Mode
 *
 * Handles playback controls and displays AI Continue status
 */
public class PlayerFragment extends Fragment implements MusicService.PlaybackListener {

    private static final String TAG = "PlayerFragment";

    // UI Elements
    private TextView tvSongTitle;
    private TextView tvSongArtist;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekbarProgress;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnPrevious;
    private ImageButton btnPlaybackMode;
    private ImageButton btnShuffle;

    // 🤖 AI Continue UI
    private LinearLayout layoutAiStatus;
    private TextView tvAiStatus;
    private ImageButton btnAiSettings;
    private TextView tvQueueInfo;

    // Service
    private MusicService musicService;

    // Update handler
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    // State
    private boolean isUserSeeking = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player, container, false);

        initializeViews(view);
        setupListeners();

        return view;
    }

    @SuppressLint("WrongViewCast")
    private void initializeViews(View view) {
        // Basic controls
        tvSongTitle = view.findViewById(R.id.tv_song_title);
        tvSongArtist = view.findViewById(R.id.tv_song_artist);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        seekbarProgress = view.findViewById(R.id.seekbar_progress);
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        btnNext = view.findViewById(R.id.btn_next);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnPlaybackMode = view.findViewById(R.id.btn_playback_mode);
        btnShuffle = view.findViewById(R.id.btn_shuffle);

        // 🤖 AI Continue UI
        layoutAiStatus = view.findViewById(R.id.layout_ai_status);
        tvAiStatus = view.findViewById(R.id.tv_ai_status);
        btnAiSettings = view.findViewById(R.id.btn_ai_settings);
        tvQueueInfo = view.findViewById(R.id.tv_queue_info);
    }

    private void setupListeners() {
        // Play/Pause
        btnPlayPause.setOnClickListener(v -> {
            if (musicService == null) return;

            if (musicService.isPlaying()) {
                musicService.pause();
            } else {
                musicService.resume();
            }
        });

        // Next
        btnNext.setOnClickListener(v -> {
            if (musicService != null) {
                musicService.playNext();
            }
        });

        // Previous
        btnPrevious.setOnClickListener(v -> {
            if (musicService != null) {
                musicService.playPrevious();
            }
        });

        // 🤖 Playback Mode (Normal → Repeat All → Repeat One → AI Continue)
        btnPlaybackMode.setOnClickListener(v -> {
            if (musicService == null) return;

            MusicService.PlaybackMode newMode = musicService.cyclePlaybackMode();
            updatePlaybackModeUI(newMode);

            String modeText = getPlaybackModeText(newMode);
            Toast.makeText(requireContext(), modeText, Toast.LENGTH_SHORT).show();
        });

        // 🤖 AI Settings
        btnAiSettings.setOnClickListener(v -> showAISettingsDialog());

        // Seekbar
        seekbarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
                isUserSeeking = false;
            }
        });
    }

    /**
     * Bind to music service
     */
    public void bindToService(MusicService service) {
        this.musicService = service;
        service.addListener(this);

        updateUI();
        startProgressUpdates();

        // Update playback mode UI
        updatePlaybackModeUI(service.getPlaybackMode());
    }

    /**
     * Update playback mode icon
     */
    private void updatePlaybackModeUI(MusicService.PlaybackMode mode) {
        if (btnPlaybackMode == null) return;

        switch (mode) {
            case NORMAL:
                btnPlaybackMode.setImageResource(R.drawable.ic_repeat_off);
                btnPlaybackMode.setAlpha(0.5f);
                layoutAiStatus.setVisibility(View.GONE);
                break;

            case REPEAT_ALL:
                btnPlaybackMode.setImageResource(R.drawable.ic_repeat_all);
                btnPlaybackMode.setAlpha(1.0f);
                layoutAiStatus.setVisibility(View.GONE);
                break;

            case REPEAT_ONE:
                btnPlaybackMode.setImageResource(R.drawable.ic_repeat_one);
                btnPlaybackMode.setAlpha(1.0f);
                layoutAiStatus.setVisibility(View.GONE);
                break;

            case AI_CONTINUE:
                btnPlaybackMode.setImageResource(R.drawable.ic_ai_continue);
                btnPlaybackMode.setAlpha(1.0f);
                layoutAiStatus.setVisibility(View.VISIBLE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    updateAIStatus();
                }
                break;
        }
    }

    /**
     * 🤖 Update AI Continue status display
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void updateAIStatus() {
        if (musicService == null || tvAiStatus == null) return;

        int queueSize = musicService.getPlaylist().size();
        int currentIndex = musicService.getCurrentIndex();
        int remaining = queueSize - currentIndex - 1;
        int threshold = musicService.getAIContinueThreshold();

        String status = String.format(Locale.ROOT,
                "🤖 AI Continue: %d songs in queue (%d remaining)",
                queueSize,
                remaining
        );

        if (remaining <= threshold) {
            status += " • Adding more...";
            tvAiStatus.setTextColor(getResources().getColor(R.color.ai_accent_bright, null));
        } else {
            tvAiStatus.setTextColor(getResources().getColor(R.color.ai_accent, null));
        }

        tvAiStatus.setText(status);
    }

    /**
     * 🤖 Show AI Continue settings dialog
     */
    private void showAISettingsDialog() {
        if (musicService == null) return;

        int currentThreshold = musicService.getAIContinueThreshold();

        String[] options = {
                "Add songs when 3 remaining (Conservative)",
                "Add songs when 5 remaining (Balanced)",
                "Add songs when 10 remaining (Aggressive)",
                "Add songs when 15 remaining (Very Aggressive)"
        };

        int[] thresholds = {3, 5, 10, 15};

        int selectedIndex = 1;  // Default to 5
        for (int i = 0; i < thresholds.length; i++) {
            if (thresholds[i] == currentThreshold) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("🤖 AI Continue Settings")
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    musicService.setAIContinueThreshold(thresholds[which]);
                    Toast.makeText(requireContext(),
                            "AI will add songs when " + thresholds[which] + " remaining",
                            Toast.LENGTH_SHORT).show();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        updateAIStatus();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Get playback mode display text
     */
    private String getPlaybackModeText(MusicService.PlaybackMode mode) {
        switch (mode) {
            case NORMAL:
                return "🔁 Normal Playback";
            case REPEAT_ALL:
                return "🔁 Repeat All";
            case REPEAT_ONE:
                return "🔂 Repeat One";
            case AI_CONTINUE:
                return "🤖 AI Continue Mode";
            default:
                return "Unknown Mode";
        }
    }

    /**
     * Update UI with current song info
     */
    private void updateUI() {
        if (musicService == null) return;

        Song currentSong = musicService.getCurrentSong();
        if (currentSong != null) {
            tvSongTitle.setText(currentSong.getTitle());
            tvSongArtist.setText(currentSong.getArtist());

            int duration = musicService.getDuration();
            seekbarProgress.setMax(duration);
            tvTotalTime.setText(formatTime(duration));
        }

        // Update play/pause button
        if (musicService.isPlaying()) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }

        // Update AI status if in AI Continue mode
        if (musicService.getPlaybackMode() == MusicService.PlaybackMode.AI_CONTINUE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                updateAIStatus();
            }
        }
    }

    /**
     * Start progress updates
     */
    private void startProgressUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (musicService != null && !isUserSeeking) {
                    int position = musicService.getCurrentPosition();
                    seekbarProgress.setProgress(position);
                    tvCurrentTime.setText(formatTime(position));

                    // Update AI status periodically
                    if (musicService.getPlaybackMode() == MusicService.PlaybackMode.AI_CONTINUE) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            updateAIStatus();
                        }
                    }
                }
                updateHandler.postDelayed(this, 1000);
            }
        };
        updateHandler.post(updateRunnable);
    }

    /**
     * Format time in mm:ss
     */
    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.ROOT,"%d:%02d", minutes, seconds);
    }

    // ===== PlaybackListener Implementation =====

    @Override
    public void onNowPlaying(Song song) {
        updateUI();
    }

    @Override
    public void onPlaybackPaused() {
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
    }

    @Override
    public void onPlaybackResumed() {
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
    }

    @Override
    public void onPlaybackEnded() {
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
    }

    @Override
    public void onPlaylistUpdated() {
        // 🤖 Playlist updated (AI added songs!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateAIStatus();
        }

        if (musicService != null &&
                musicService.getPlaybackMode() == MusicService.PlaybackMode.AI_CONTINUE) {
            Toast.makeText(requireContext(),
                    "🤖 AI added new songs to queue!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPlaybackModeChanged(MusicService.PlaybackMode mode) {
        updatePlaybackModeUI(mode);
    }

    // ===== Lifecycle =====

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }

        if (musicService != null) {
            musicService.removeListener(this);
        }
    }
}