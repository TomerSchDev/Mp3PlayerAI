package com.tomersch.mp3playerai.activities;

import static com.tomersch.mp3playerai.utils.UiUtils.dp;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.adapters.MusicPagerAdapter;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.models.Tab;
import com.tomersch.mp3playerai.player.LibraryProvider;
import com.tomersch.mp3playerai.player.PlayerController;
import com.tomersch.mp3playerai.utils.LibraryRepository;
import com.tomersch.mp3playerai.utils.ManualFileScanner;
import com.tomersch.mp3playerai.utils.MusicService;
import com.tomersch.mp3playerai.utils.SongCacheManager;
import com.tomersch.mp3playerai.utils.UserActivityLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MainActivity
 * - owns UI (tabs + mini player + bottom sheet full player)
 * - owns scanning + initializes LibraryRepository
 * - binds to MusicService and implements PlayerController
 */
public class MainActivity extends AppCompatActivity
        implements PlayerController, LibraryProvider, MusicService.Callback {

    private static final String TAG = "MP3Player";
    private static final int PERMISSION_REQUEST_CODE = 100;


    //new PlayList Button
    // Tabs
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // Toolbar buttons
    private ImageButton btnRefresh;
    private ImageButton btnViewLogs;

    // Mini player
    private LinearLayout miniPlayer;
    private TextView miniPlayerTitle;
    private TextView miniPlayerArtist;
    private ImageButton miniPlayerPlayPause;
    private ImageButton miniPlayerNext;

    // Full player bottom sheet
    private View bottomSheetPlayer;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    private TextView playerSongTitle;
    private TextView playerArtistName;
    private ImageButton playerBtnPrevious;
    private ImageButton playerBtnPlayPause;
    private ImageButton playerBtnNext;
    private ImageButton btnMinimize;

    // Optional queue list in full player
    private RecyclerView playerQueue;
    private QueueAdapter queueAdapter;

    // Core data
    private LibraryRepository library;
    private SongCacheManager cacheManager;
    private UserActivityLogger activityLogger;

    // Service
    private MusicService musicService;
    private boolean serviceBound = false;

    // Pending play if user taps before bind completes
    private ArrayList<Song> pendingQueue = null;
    private int pendingIndex = -1;

    private final Runnable repoListener = this::onRepositoryChanged;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceBound = true;

            musicService.setCallback(MainActivity.this);
            musicService.setActivityLogger(activityLogger);

            // Apply pending request
            if (pendingQueue != null && pendingIndex >= 0) {
                musicService.setQueue(pendingQueue, pendingIndex);
                pendingQueue = null;
                pendingIndex = -1;
            }

            // Sync UI
            Song current = musicService.getCurrentSong();
            if (current != null) {
                onSongChanged(current);
                onPlaybackState(musicService.isPlaying());
                showMiniPlayer();
                syncQueueUI();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            musicService = null;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_main);
            View root = findViewById(android.R.id.content);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = insets.getSystemWindowInsetBottom();

                // push mini player above nav bar
                View mini = findViewById(R.id.miniPlayer);
                if (mini != null) mini.setPadding(mini.getPaddingLeft(), mini.getPaddingTop(),
                        mini.getPaddingRight(), bottom);



                return insets;
            });
            root.requestApplyInsets();

            library = LibraryRepository.getInstance(this);
            cacheManager = new SongCacheManager(this);
            activityLogger = new UserActivityLogger(this);
            activityLogger.logAppOpened();

            bindViews();
            setupTabs();
            setupMiniPlayer();
            setupFullPlayer();
            setupButtons();
            library.addListener(repoListener);

            if (checkPermissions()) loadSongs();
            else requestPermissions();

        } catch (Exception e) {
            Log.e(TAG, "onCreate error: " + e.getMessage(), e);
            Toast.makeText(this, "Error starting app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /* ============================================================
       LibraryProvider
       ============================================================ */

    @Override
    public LibraryRepository getLibraryRepository() {
        return library;
    }

    /* ============================================================
       PlayerController
       ============================================================ */

    @Override
    public void playQueue(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty() || startIndex < 0 || startIndex >= songs.size()) return;

        // Ensure service exists
        startService(new Intent(this, MusicService.class));

        ArrayList<Song> q = new ArrayList<>(songs);

        if (serviceBound && musicService != null) {
            musicService.setQueue(q, startIndex);
        } else {
            pendingQueue = q;
            pendingIndex = startIndex;
        }

        showMiniPlayer();
        changePlayer(true);
    }

    @Override
    public void playSong(Song song) {
        if (song == null) return;

        // Prefer playing in context of "All songs" queue
        List<Song> all = library.getAllSongs();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (song.getPath().equals(all.get(i).getPath())) {
                idx = i;
                break;
            }
        }

        if (idx >= 0) playQueue(all, idx);
        else {
            ArrayList<Song> single = new ArrayList<>();
            single.add(song);
            playQueue(single, 0);
        }
    }

    @Override
    public void togglePlayPause() {
        if (!serviceBound || musicService == null) return;
        if (musicService.isPlaying()) musicService.pause();
        else musicService.resume();
    }

    @Override
    public void next() {
        if (!serviceBound || musicService == null) return;
        musicService.next();
    }

    @Override
    public void previous() {
        if (!serviceBound || musicService == null) return;
        musicService.previous();
    }

    @Override
    public Song getCurrentSong() {
        return (serviceBound && musicService != null) ? musicService.getCurrentSong() : null;
    }

    @Override
    public boolean isPlaying() {
        return serviceBound && musicService != null && musicService.isPlaying();
    }

    /* ============================================================
       MusicService.Callback
       ============================================================ */

    @Override
    public void onSongChanged(Song song) {
        if (song == null) return;

        // Mini
        miniPlayerTitle.setText(song.getTitle());
        miniPlayerArtist.setText(song.getArtist());

        // Full
        if (playerSongTitle != null) playerSongTitle.setText(song.getTitle());
        if (playerArtistName != null) playerArtistName.setText(song.getArtist());

        showMiniPlayer();
        syncQueueUI();
    }

    @Override
    public void onPlaybackState(boolean playing) {
        int icon = playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        miniPlayerPlayPause.setImageResource(icon);
        if (playerBtnPlayPause != null) playerBtnPlayPause.setImageResource(icon);
    }

    private void bindViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);


        btnRefresh = findViewById(R.id.btnRefresh);
        btnViewLogs = findViewById(R.id.btnViewLogs);

        miniPlayer = findViewById(R.id.miniPlayer);
        miniPlayerTitle = findViewById(R.id.miniPlayerTitle);
        miniPlayerArtist = findViewById(R.id.miniPlayerArtist);
        miniPlayerPlayPause = findViewById(R.id.miniPlayerPlayPause);
        miniPlayerNext = findViewById(R.id.miniPlayerNext);
        btnMinimize = findViewById(R.id.playerBtnMinimize);
        if (btnMinimize != null) btnMinimize.setOnClickListener(v -> changePlayer(false));
        bottomSheetPlayer = findViewById(R.id.bottomSheetPlayer);
        if (bottomSheetPlayer == null) {
            Log.e(TAG, "bottomSheetPlayer not found. Ensure bottom_sheet_player.xml root has android:id=\"@+id/bottomSheetPlayer\".");
        }

        playerSongTitle = findViewById(R.id.playerSongTitle);
        playerArtistName = findViewById(R.id.playerArtistName);
        playerBtnPrevious = findViewById(R.id.playerBtnPrevious);
        playerBtnPlayPause = findViewById(R.id.playerBtnPlayPause);
        playerBtnNext = findViewById(R.id.playerBtnNext);

        // Optional (only if you add RecyclerView in layout)
        playerQueue = findViewById(R.id.playerQueue);
    }

    private void setupTabs() {
        MusicPagerAdapter pagerAdapter = new MusicPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        viewPager.setCurrentItem(Tab.ALL.ordinal(), false);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(Tab.labels[position])
        ).attach();
    }

    private void setupMiniPlayer() {
        miniPlayer.setOnClickListener(v -> changePlayer(true));
        miniPlayerPlayPause.setOnClickListener(v -> togglePlayPause());
        miniPlayerNext.setOnClickListener(v -> next());
    }

    private void setupFullPlayer() {
        if (bottomSheetPlayer == null) return;

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetPlayer);
        bottomSheetBehavior.setPeekHeight(dp(this,64));
        bottomSheetBehavior.setHideable(false);
        changePlayer(false); //start with hidden player


        if (playerBtnPrevious != null) playerBtnPrevious.setOnClickListener(v -> previous());
        if (playerBtnPlayPause != null) playerBtnPlayPause.setOnClickListener(v -> togglePlayPause());
        if (playerBtnNext != null) playerBtnNext.setOnClickListener(v -> next());

        if (playerQueue != null) {
            playerQueue.setLayoutManager(new LinearLayoutManager(this));
            queueAdapter = new QueueAdapter();
            playerQueue.setAdapter(queueAdapter);
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomSheetPlayer, (v, insets) -> {
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    navBars.bottom
            );
            return insets;
        });
    }

    private void setupButtons() {
        btnRefresh.setOnClickListener(v -> {
            if (checkPermissions()) {
                Toast.makeText(this, "Rescanning for songs...", Toast.LENGTH_SHORT).show();
                loadSongs();
            } else {
                Toast.makeText(this, "Please grant storage permission", Toast.LENGTH_SHORT).show();
                requestPermissions();
            }
        });

        btnViewLogs.setOnClickListener(v -> startActivity(new Intent(this, LogsActivity.class)));

        btnRefresh.setOnLongClickListener(v -> {
            cacheManager.clearCache();
            Toast.makeText(this, "Cache cleared!", Toast.LENGTH_SHORT).show();
            if (checkPermissions()) loadSongs();
            return true;
        });
    }

    /* ============================================================
       Repo changes
       ============================================================ */

    private void onRepositoryChanged() {
        // Fragments should read from LibraryRepository in onResume/onViewCreated.
        // We still keep player queue synced.
        syncQueueUI();
    }

    /* ============================================================
       Song loading
       ============================================================ */

    private void loadSongs() {
        Log.d(TAG, "========== Starting to load songs ==========");

        if (cacheManager.hasCachedSongs()) {
            List<Song> cachedSongs = cacheManager.loadCachedSongs();
            library.initSongs(cachedSongs);
            Toast.makeText(this, "Loaded " + cachedSongs.size() + " songs from cache", Toast.LENGTH_SHORT).show();
        }

        Toast.makeText(this, "Checking for new songs...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            List<Song> scannedSongs = ManualFileScanner.scanForAudioFiles(this);
            SongCacheManager.ScanResult result = cacheManager.compareAndUpdate(scannedSongs);

            runOnUiThread(() -> {
                List<Song> allSongs = result.getAllSongs();
                library.initSongs(allSongs);

                if (!result.hasChanges()) {
                    Toast.makeText(this, "Library is up to date (" + allSongs.size() + " songs)", Toast.LENGTH_SHORT).show();
                } else {
                    int added = result.getAddedSongs().size();
                    int removed = result.getRemovedSongs().size();
                    activityLogger.logLibraryScan(allSongs.size(), added, removed);
                    Toast.makeText(this,
                            "Updated: +" + added + ", -" + removed + " (" + allSongs.size() + " total)",
                            Toast.LENGTH_LONG).show();
                }

                if (allSongs.isEmpty()) {
                    Toast.makeText(this, "No audio files found", Toast.LENGTH_LONG).show();
                }
            });
        }).start();

        Log.d(TAG, "========== Finished loading songs ==========");
    }

    /* ============================================================
       Bottom sheet helpers
       ============================================================ */

    private void showMiniPlayer() {
        if (miniPlayer != null) miniPlayer.setVisibility(View.VISIBLE);
    }

    private void changePlayer(boolean toExpend) {
        if (bottomSheetBehavior == null) return;
        bottomSheetBehavior.setState(toExpend ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
        if (!toExpend){
            btnMinimize.setVisibility(View.GONE);
            btnMinimize.setActivated(false);
        } else {
            btnMinimize.setVisibility(View.VISIBLE);
            btnMinimize.setActivated(true);
        }
    }

    private void syncQueueUI() {
        if (!serviceBound || musicService == null || queueAdapter == null) return;
        queueAdapter.submit(musicService.getQueue(), musicService.getCurrentIndex());
    }

    /* ============================================================
       Permissions
       ============================================================ */

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
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
                loadSongs();
            } else {
                Toast.makeText(this, "Permission denied - cannot load songs", Toast.LENGTH_LONG).show();
            }
        }
    }

    /* ============================================================
       Lifecycle
       ============================================================ */

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), serviceConnection, Context.BIND_AUTO_CREATE);
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
        if (library != null) library.removeListener(repoListener);
        if (activityLogger != null) activityLogger.logAppClosed();
    }



    /* ============================================================
       Internal queue adapter (no extra class file needed)
       ============================================================ */

    private final class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {
        private final List<Song> items = new ArrayList<>();
        private int currentIndex = -1;

        void submit(List<Song> queue, int idx) {
            items.clear();
            if (queue != null) items.addAll(queue);
            currentIndex = idx;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Song s = items.get(position);
            holder.title.setText(s.getTitle());
            holder.subtitle.setText(s.getArtist());

            holder.itemView.setAlpha(position == currentIndex ? 1.0f : 0.6f);

            holder.itemView.setOnClickListener(v -> {
                if (!serviceBound || musicService == null) return;
                musicService.playAt(position);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
