package com.tomersch.mp3playerai.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.ai.AILearningManager;
import com.tomersch.mp3playerai.ai.SongMatcher;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.utils.LibraryRepository;
import com.tomersch.mp3playerai.utils.SongCacheManager;
import com.tomersch.mp3playerai.utils.UserActivityLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Developer Activity - Control scores and reset saved data
 */
public class DevActivity extends AppCompatActivity {

    private static final String TAG = "DevActivity";

    // UI Components
    private TextView tvAiStats;
    private TextView tvCacheStats;
    private TextView tvLogsStats;
    private TextView tvLibraryStats;

    private Button btnResetAiLearning;
    private Button btnClearCache;
    private Button btnClearLogs;
    private Button btnClearFavorites;
    private Button btnClearPlaylists;
    private Button btnRebuildAiDb;
    private Button btnViewSongScores;
    private Button btnBack;

    // Managers
    private AILearningManager learningManager;
    private SongCacheManager cacheManager;
    private UserActivityLogger activityLogger;
    private LibraryRepository libraryRepo;
    private SongMatcher songMatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev);

        // Initialize managers
        learningManager = new AILearningManager(this);
        cacheManager = new SongCacheManager(this);
        activityLogger = new UserActivityLogger(this);
        libraryRepo = LibraryRepository.getInstance(this);
        songMatcher = new SongMatcher(this);

        bindViews();
        setupButtons();
        refreshStats();
    }

    private void bindViews() {
        tvAiStats = findViewById(R.id.tvAiStats);
        tvCacheStats = findViewById(R.id.tvCacheStats);
        tvLogsStats = findViewById(R.id.tvLogsStats);
        tvLibraryStats = findViewById(R.id.tvLibraryStats);

        btnResetAiLearning = findViewById(R.id.btnResetAiLearning);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        btnClearFavorites = findViewById(R.id.btnClearFavorites);
        btnClearPlaylists = findViewById(R.id.btnClearPlaylists);
        btnRebuildAiDb = findViewById(R.id.btnRebuildAiDb);
        btnViewSongScores = findViewById(R.id.btnViewSongScores);
        btnBack = findViewById(R.id.btnBack);
        Button btnModuleControl = findViewById(R.id.btnModuleControl);
        btnModuleControl.setOnClickListener(v->startActivity(new Intent(this, ModelManagerActivity.class)));
    }

    private void setupButtons() {
        btnResetAiLearning.setOnClickListener(v -> confirmReset(
                "Reset AI Learning?",
                "This will clear all learned preferences and song scores.",
                this::resetAiLearning
        ));

        btnClearCache.setOnClickListener(v -> confirmReset(
                "Clear Song Cache?",
                "This will force a full library rescan on next app start.",
                this::clearCache
        ));

        btnClearLogs.setOnClickListener(v -> confirmReset(
                "Clear Activity Logs?",
                "This will delete all recorded user activity.",
                this::clearLogs
        ));

        btnClearFavorites.setOnClickListener(v -> confirmReset(
                "Clear All Favorites?",
                "This will remove all songs from favorites.",
                this::clearFavorites
        ));

        btnClearPlaylists.setOnClickListener(v -> confirmReset(
                "Delete All Playlists?",
                "This will permanently delete all playlists.",
                this::clearPlaylists
        ));

        btnRebuildAiDb.setOnClickListener(v -> confirmReset(
                "Rebuild AI Database?",
                "This will delete and rebuild the local AI database.",
                this::rebuildAiDatabase
        ));

        btnViewSongScores.setOnClickListener(v -> showSongScoresDialog());

        btnBack.setOnClickListener(v -> finish());
    }

    private void confirmReset(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    action.run();
                    refreshStats();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ===== RESET ACTIONS =====

    private void resetAiLearning() {
        learningManager.reset();
        Toast.makeText(this, "✅ AI Learning data reset!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "AI Learning reset");
    }

    private void clearCache() {
        cacheManager.clearCache();
        Toast.makeText(this, "✅ Song cache cleared!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Cache cleared");
    }

    private void clearLogs() {
        activityLogger.clearLogs();
        Toast.makeText(this, "✅ Activity logs cleared!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Logs cleared");
    }

    private void clearFavorites() {
        List<Song> favorites = libraryRepo.getFavoriteSongs();
        for (Song song : favorites) {
            libraryRepo.toggleFavorite(song);
        }
        Toast.makeText(this, "✅ Favorites cleared!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Favorites cleared");
    }

    private void clearPlaylists() {
        List<com.tomersch.mp3playerai.models.Playlist> playlists = libraryRepo.getPlaylists();
        for (com.tomersch.mp3playerai.models.Playlist playlist : playlists) {
            libraryRepo.deletePlaylist(playlist.getId());
        }
        Toast.makeText(this, "✅ All playlists deleted!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Playlists cleared");
    }

    private void rebuildAiDatabase() {
        songMatcher.deleteLocalDatabase();
        Toast.makeText(this, "✅ AI Database deleted! Will rebuild on next library scan.", Toast.LENGTH_LONG).show();
        Log.d(TAG, "AI Database reset");
        songMatcher.buildLocalDatabase(LibraryRepository.getInstance(this).getAllSongs());
    }

    // ===== STATS DISPLAY =====

    private void refreshStats() {
        // AI Learning Stats
        String aiStats = learningManager.getStats();
        tvAiStats.setText(aiStats);

        // Cache Stats
        boolean hasCache = cacheManager.hasCachedSongs();
        int cachedCount = hasCache ? cacheManager.loadCachedSongs().size() : 0;
        tvCacheStats.setText(String.format(Locale.ROOT,
                "Cached Songs: %d\nHas Cache: %s",
                cachedCount,
                hasCache ? "Yes" : "No"
        ));

        // Logs Stats
        UserActivityLogger.ListeningStats logsStats = activityLogger.getListeningStats();
        tvLogsStats.setText(String.format(
                "Total Logs: %d\n%s",
                activityLogger.getAllLogs().size(),
                logsStats.toString()
        ));

        // Library Stats
        int favoritesCount = libraryRepo.getFavoriteSongs().size();
        int playlistsCount = libraryRepo.getPlaylists().size();
        int totalSongs = libraryRepo.getAllSongs().size();
        tvLibraryStats.setText(String.format(
                "Total Songs: %d\nFavorites: %d\nPlaylists: %d",
                totalSongs,
                favoritesCount,
                playlistsCount
        ));
    }

    // ===== SONG SCORES DIALOG =====

    private void showSongScoresDialog() {
        Map<String, Float> allScores = learningManager.getAllSongScores();

        if (allScores.isEmpty()) {
            Toast.makeText(this, "No song scores yet!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get songs with scores
        List<SongScoreItem> scoreItems = new ArrayList<>();
        List<Song> allSongs = libraryRepo.getAllSongs();

        for (Song song : allSongs) {
            if (allScores.containsKey(song.getPath())) {
                float score = allScores.get(song.getPath());
                scoreItems.add(new SongScoreItem(song, score));
            }
        }

        // Sort by score (highest first)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            scoreItems.sort((a, b) -> Float.compare(b.score, a.score));
        }

        // Show dialog with RecyclerView
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Song Scores (" + scoreItems.size() + ")");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_song_scores, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rvSongScores);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        SongScoreAdapter adapter = new SongScoreAdapter(scoreItems, this::editSongScore);
        recyclerView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void editSongScore(SongScoreItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Score: " + item.song.getTitle());

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_score, null);
        EditText etScore = dialogView.findViewById(R.id.etScore);
        TextView tvCurrentScore = dialogView.findViewById(R.id.tvCurrentScore);

        tvCurrentScore.setText(String.format(Locale.ROOT,"Current Score: %.2f", item.score));
        etScore.setText(String.valueOf(item.score));

        builder.setView(dialogView);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                float newScore = Float.parseFloat(etScore.getText().toString());
                // Clamp between -1.0 and 1.0
                newScore = Math.max(-1.0f, Math.min(1.0f, newScore));

                // Update the score
                updateSongScore(item.song.getPath(), newScore);

                Toast.makeText(this, "Score updated!", Toast.LENGTH_SHORT).show();
                refreshStats();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);

        builder.setNeutralButton("Reset to 0", (dialog, which) -> {
            updateSongScore(item.song.getPath(), 0.0f);
            Toast.makeText(this, "Score reset to 0!", Toast.LENGTH_SHORT).show();
            refreshStats();
        });

        builder.show();
    }

    private void updateSongScore(String songPath, float newScore) {
        // Use the new setSongScore method
        learningManager.setSongScore(songPath, newScore);
        Log.d(TAG, "Updated score for " + songPath + " to " + newScore);
    }

    // ===== HELPER CLASSES =====

    private static class SongScoreItem {
        Song song;
        float score;

        SongScoreItem(Song song, float score) {
            this.song = song;
            this.score = score;
        }
    }

    private static class SongScoreAdapter extends RecyclerView.Adapter<SongScoreAdapter.ViewHolder> {
        private List<SongScoreItem> items;
        private OnScoreClickListener listener;

        interface OnScoreClickListener {
            void onScoreClick(SongScoreItem item);
        }

        SongScoreAdapter(List<SongScoreItem> items, OnScoreClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_song_score, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SongScoreItem item = items.get(position);
            holder.bind(item, listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            TextView tvArtist;
            TextView tvScore;
            Button btnEdit;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvSongTitle);
                tvArtist = itemView.findViewById(R.id.tvSongArtist);
                tvScore = itemView.findViewById(R.id.tvScore);
                btnEdit = itemView.findViewById(R.id.btnEditScore);
            }

            void bind(SongScoreItem item, OnScoreClickListener listener) {
                tvTitle.setText(item.song.getTitle());
                tvArtist.setText(item.song.getArtist());
                tvScore.setText(String.format(Locale.ROOT,"Score: %.2f", item.score));

                // Color based on score
                int color;
                if (item.score > 0.3f) {
                    color = 0xFF4CAF50; // Green
                } else if (item.score < -0.3f) {
                    color = 0xFFF44336; // Red
                } else {
                    color = 0xFFFF9800; // Orange
                }
                tvScore.setTextColor(color);

                btnEdit.setOnClickListener(v -> listener.onScoreClick(item));
            }
        }
    }
}