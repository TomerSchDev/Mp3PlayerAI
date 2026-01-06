package com.tomersch.mp3playerai.activities;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.adapters.MusicPagerAdapter;
import com.tomersch.mp3playerai.adapters.SongAdapter;
import com.tomersch.mp3playerai.fragments.AllSongsFragment;
import com.tomersch.mp3playerai.fragments.FavoritesFragment;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.utils.ManualFileScanner;
import com.tomersch.mp3playerai.utils.MusicService;
import com.tomersch.mp3playerai.utils.SongCacheManager;
import com.tomersch.mp3playerai.utils.UserActivityLogger;
import com.tomersch.mp3playerai.utils.FavoritesManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private static final String TAG = "MP3Player";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] TAB_TITLES = {"Favorites", "Playlists", "All", "Folders", "Custom"};

    // UI Components
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MusicPagerAdapter pagerAdapter;

    // Mini Player
    private LinearLayout miniPlayer;
    private TextView miniPlayerTitle;
    private TextView miniPlayerArtist;
    private ImageButton miniPlayerPlayPause;
    private ImageButton miniPlayerNext;

    // Buttons
    private ImageButton btnRefresh;
    private ImageButton btnViewLogs;

    // Utils
    private SongCacheManager cacheManager;
    private UserActivityLogger activityLogger;
    private FavoritesManager favoritesManager;
    private MusicService musicService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceBound = true;

            musicService.setOnSongChangeListener(new MusicService.OnSongChangeListener() {
                @Override
                public void onSongChanged(Song song) {
                    updateUI(song);
                }

                @Override
                public void onPlaybackStateChanged(boolean isPlaying) {
                    updatePlayPauseButtons(isPlaying);
                }
            });

            if (musicService.getCurrentSong() != null) {
                updateUI(musicService.getCurrentSong());
                updatePlayPauseButtons(musicService.isPlaying());
                showMiniPlayer();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Log.d(TAG, "onCreate started");
            setContentView(R.layout.activity_main);

            cacheManager = new SongCacheManager(this);
            activityLogger = new UserActivityLogger(this);
            favoritesManager = new FavoritesManager(this);
            activityLogger.logAppOpened();

            Log.d(TAG, "Initializing views");
            initializeViews();

            Log.d(TAG, "Setting up tabs");
            setupTabs();

            Log.d(TAG, "Setting up mini player");
            setupMiniPlayer();

            Log.d(TAG, "Setting up buttons");
            setupButtons();

            // Listen for favorites changes to update fragments
            favoritesManager.addListener(this::updateFavoritesTab);

            if (checkPermissions()) {
                loadSongs();
            } else {
                requestPermissions();
            }

            Log.d(TAG, "onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this,
                    getString(R.string.toast_error_starting_app, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        try {
            tabLayout = findViewById(R.id.tabLayout);
            viewPager = findViewById(R.id.viewPager);
            btnRefresh = findViewById(R.id.btnRefresh);
            btnViewLogs = findViewById(R.id.btnViewLogs);

            // Mini Player
            miniPlayer = findViewById(R.id.miniPlayer);
            miniPlayerTitle = findViewById(R.id.miniPlayerTitle);
            miniPlayerArtist = findViewById(R.id.miniPlayerArtist);
            miniPlayerPlayPause = findViewById(R.id.miniPlayerPlayPause);
            miniPlayerNext = findViewById(R.id.miniPlayerNext);

            Log.d(TAG, "All views initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage(), e);
            throw e;
        }
    }

    private void setupTabs() {
        try {
            pagerAdapter = new MusicPagerAdapter(this);
            viewPager.setAdapter(pagerAdapter);

            // Set default tab to "All" (index 2)
            viewPager.setCurrentItem(2, false);

            new TabLayoutMediator(tabLayout, viewPager,
                    (tab, position) -> tab.setText(TAB_TITLES[position])
            ).attach();

            // CRITICAL: Set FavoritesManager on fragments immediately after ViewPager creates them
            // Use a longer delay to ensure fragments are fully created
            viewPager.postDelayed(() -> {
                Log.d(TAG, "Setting up fragment listeners and FavoritesManager");

                try {
                    AllSongsFragment allSongsFragment = pagerAdapter.getAllSongsFragment();
                    if (allSongsFragment != null) {
                        allSongsFragment.setSongClickListener(this);
                        allSongsFragment.setFavoritesManager(favoritesManager);
                        Log.d(TAG, "AllSongsFragment: FavoritesManager set");
                    } else {
                        Log.e(TAG, "AllSongsFragment is NULL!");
                    }

                    FavoritesFragment favoritesFragment = pagerAdapter.getFavoritesFragment();
                    if (favoritesFragment != null) {
                        favoritesFragment.setSongClickListener(this);
                        favoritesFragment.setFavoritesManager(favoritesManager);
                        Log.d(TAG, "FavoritesFragment: FavoritesManager set");
                    } else {
                        Log.e(TAG, "FavoritesFragment is NULL!");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up fragments: " + e.getMessage(), e);
                }
            }, 100); // Small delay to ensure fragments are created

            Log.d(TAG, "Tabs setup successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up tabs: " + e.getMessage(), e);
            throw e;
        }
    }

    private void setupMiniPlayer() {
        miniPlayer.setOnClickListener(v -> Toast.makeText(this, R.string.toast_full_player_soon, Toast.LENGTH_SHORT).show());

        miniPlayerPlayPause.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pauseSong();
                } else {
                    musicService.resumeSong();
                }
            }
        });

        miniPlayerNext.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                musicService.playNext();
            }
        });
    }

    private void setupButtons() {
        btnRefresh.setOnClickListener(v -> {
            if (checkPermissions()) {
                Toast.makeText(this, R.string.toast_rescanning, Toast.LENGTH_SHORT).show();
                loadSongs();
            } else {
                Toast.makeText(this, R.string.toast_grant_permission,
                        Toast.LENGTH_SHORT).show();
                requestPermissions();
            }
        });

        btnViewLogs.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogsActivity.class);
            startActivity(intent);
        });

        btnRefresh.setOnLongClickListener(v -> {
            cacheManager.clearCache();
            Toast.makeText(this, R.string.toast_cache_cleared,
                    Toast.LENGTH_SHORT).show();
            if (checkPermissions()) {
                loadSongs();
            }
            return true;
        });
    }

    private void loadSongs() {
        Log.d(TAG, "========== Starting to load songs ==========");

        if (cacheManager.hasCachedSongs()) {
            List<Song> cachedSongs = cacheManager.loadCachedSongs();
            updateFragments(cachedSongs);

            Log.d(TAG, "Loaded " + cachedSongs.size() + " songs from cache");
            Toast.makeText(this,
                    getString(R.string.toast_loaded_from_cache, cachedSongs.size()),
                    Toast.LENGTH_SHORT).show();
        }

        Toast.makeText(this, R.string.toast_checking_new_songs, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            List<Song> scannedSongs = ManualFileScanner.scanForAudioFiles(this);
            SongCacheManager.ScanResult result = cacheManager.compareAndUpdate(scannedSongs);

            runOnUiThread(() -> {
                updateFragments(result.getAllSongs());

                if (!result.hasChanges()) {
                    Log.d(TAG, "No changes detected");
                    Toast.makeText(this,
                            getString(R.string.toast_library_up_to_date, result.getAllSongs().size()),
                            Toast.LENGTH_SHORT).show();
                } else {
                    int added = result.getAddedSongs().size();
                    int removed = result.getRemovedSongs().size();

                    activityLogger.logLibraryScan(result.getAllSongs().size(), added, removed);

                    StringBuilder message = new StringBuilder();
                    if (added > 0) {
                        message.append("Added ").append(added).append(" song").append(added > 1 ? "s" : "");
                    }
                    if (removed > 0) {
                        if (message.length() > 0) message.append(", ");
                        message.append("Removed ").append(removed).append(" song").append(removed > 1 ? "s" : "");
                    }

                    Toast.makeText(this,
                            getString(R.string.toast_songs_added_removed, message.toString(), result.getAllSongs().size()),
                            Toast.LENGTH_LONG).show();
                }

                if (result.getAllSongs().isEmpty()) {
                    Toast.makeText(this, R.string.toast_no_audio_files,
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();

        Log.d(TAG, "========== Finished loading songs ==========");
    }

    private void updateFragments(List<Song> songs) {
        try {
            Log.d(TAG, "updateFragments called with " + songs.size() + " songs");

            AllSongsFragment allSongsFragment = pagerAdapter.getAllSongsFragment();
            if (allSongsFragment != null) {
                // Make sure FavoritesManager is set before updating
                allSongsFragment.setFavoritesManager(favoritesManager);
                allSongsFragment.updateSongs(songs);
                Log.d(TAG, "AllSongsFragment updated with songs");
            } else {
                Log.e(TAG, "AllSongsFragment is NULL in updateFragments!");
            }

            updateFavoritesTab();
        } catch (Exception e) {
            Log.e(TAG, "Error updating fragments: " + e.getMessage(), e);
        }
    }

    private void updateFavoritesTab() {
        try {
            List<Song> allSongs = pagerAdapter.getAllSongsFragment().getSongs();
            List<Song> favoriteSongs = new ArrayList<>();

            for (Song song : allSongs) {
                if (favoritesManager.isFavorite(song.getPath())) {
                    favoriteSongs.add(song);
                }
            }

            pagerAdapter.getFavoritesFragment().updateFavorites(favoriteSongs);
            Log.d(TAG, "Updated favorites tab with " + favoriteSongs.size() + " songs");
        } catch (Exception e) {
            Log.e(TAG, "Error updating favorites tab: " + e.getMessage(), e);
        }
    }

    @Override
    public void onSongClick(int position) {
        List<Song> songs = pagerAdapter.getAllSongsFragment().getSongs();
        playSong(songs, position);
    }

    private void showMiniPlayer() {
        miniPlayer.setVisibility(View.VISIBLE);
    }

    private void updateUI(Song song) {
        if (song != null) {
            miniPlayerTitle.setText(song.getTitle());
            miniPlayerArtist.setText(song.getArtist());
        }
    }

    private void updatePlayPauseButtons(boolean isPlaying) {
        int icon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        miniPlayerPlayPause.setImageResource(icon);
    }

    private boolean checkPermissions() {
        boolean hasPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Android 13+ - READ_MEDIA_AUDIO permission: " +
                    (hasPermission ? "GRANTED" : "DENIED"));
        } else {
            hasPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Android 12 or below - READ_EXTERNAL_STORAGE permission: " +
                    (hasPermission ? "GRANTED" : "DENIED"));
        }
        return hasPermission;
    }

    private void requestPermissions() {
        Log.d(TAG, "Requesting permissions...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission GRANTED by user");
                loadSongs();
            } else {
                Log.e(TAG, "Permission DENIED by user");
                Toast.makeText(this, R.string.toast_permission_denied,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    public void playSong(List<Song> playlist, int position) {
        if (playlist == null || playlist.isEmpty() || position < 0 || position >= playlist.size()) {
            Log.e(TAG, "Invalid playlist or position in playSong");
            return;
        }

        Song selectedSong = playlist.get(position);
        activityLogger.logSongSelected(selectedSong, position);

        // 1. Ensure the service is started so it lives even if activity stops
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_PLAY);
        startService(intent);

        // 2. Communicate with the bound service
        if (serviceBound && musicService != null) {
            musicService.setSongList(playlist);
            musicService.setActivityLogger(activityLogger);
            musicService.playSong(position);

            // Ensure the mini player is visible and showing correct data
            showMiniPlayer();
            updateUI(selectedSong);
            updatePlayPauseButtons(true);
        } else {
            Toast.makeText(this, "Music Service not ready yet", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Standard interface for AllSongsFragment and FavoritesFragment
     */

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activityLogger != null) {
            activityLogger.logAppClosed();
        }
    }

    public List<Song> getSongList() {
        return pagerAdapter.getAllSongsFragment().getSongs();
    }

    public FavoritesManager getFavoritesManager() {
        return favoritesManager;
    }
}