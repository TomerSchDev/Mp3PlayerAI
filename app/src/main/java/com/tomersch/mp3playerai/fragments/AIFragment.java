package com.tomersch.mp3playerai.fragments;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.adapters.SongAdapter;
import com.tomersch.mp3playerai.ai.AIRecommendationEngine;
import com.tomersch.mp3playerai.models.Playlist;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.player.LibraryProvider;
import com.tomersch.mp3playerai.player.PlayerController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AIFragment extends Fragment {

    private EditText etTextQuery;
    private SeekBar seekBarHype, seekBarAggressive, seekBarMelodic;
    private SeekBar seekBarAtmospheric, seekBarCinematic, seekBarRhythmic;
    private SeekBar seekBarSongCount;
    private TextView tvHypeValue, tvAggressiveValue, tvMelodicValue;
    private TextView tvAtmosphericValue, tvCinematicValue, tvRhythmicValue;
    private TextView tvSongCountValue;
    private Button btnGenerate, btnResetSliders, btnPlayAll, btnSavePlaylist;
    private ProgressBar progressBar;
    private MaterialCardView cardResults;
    private RecyclerView recyclerViewResults;

    private Button chipEnergetic, chipChill, chipFocus, chipWorkout, chipParty;

    private AIRecommendationEngine aiEngine;
    private SongAdapter songAdapter;
    private List<Song> generatedSongs;

    // Executor for background tasks
    private ExecutorService executorService;
    private Handler mainHandler;
    private PlayerController playerController;
    private LibraryProvider libraryProvider;

    public static Fragment newInstance() {
        return new AIFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof PlayerController) {
            playerController = (PlayerController) context;
        } else {
            throw new IllegalStateException("Host activity must implement PlayerController");
        }
        if (context instanceof LibraryProvider) {
            libraryProvider = (LibraryProvider) context;
        }else {
            throw new IllegalStateException("Host activity must implement LibraryProvider");
        }

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ai, container, false);

        // Initialize executor and handler
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        initializeViews(view);
        setupListeners();

        // Initialize AI engine
        aiEngine = new AIRecommendationEngine(getContext(),"");

        // Setup RecyclerView
        generatedSongs = new ArrayList<>();
        songAdapter = new SongAdapter(generatedSongs, position -> {
            playerController.playQueue(generatedSongs, position);
        });
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewResults.setAdapter(songAdapter);

        return view;
    }

    private void initializeViews(View view) {
        etTextQuery = view.findViewById(R.id.etTextQuery);

        seekBarHype = view.findViewById(R.id.seekBarHype);
        seekBarAggressive = view.findViewById(R.id.seekBarAggressive);
        seekBarMelodic = view.findViewById(R.id.seekBarMelodic);
        seekBarAtmospheric = view.findViewById(R.id.seekBarAtmospheric);
        seekBarCinematic = view.findViewById(R.id.seekBarCinematic);
        seekBarRhythmic = view.findViewById(R.id.seekBarRhythmic);
        seekBarSongCount = view.findViewById(R.id.seekBarSongCount);

        tvHypeValue = view.findViewById(R.id.tvHypeValue);
        tvAggressiveValue = view.findViewById(R.id.tvAggressiveValue);
        tvMelodicValue = view.findViewById(R.id.tvMelodicValue);
        tvAtmosphericValue = view.findViewById(R.id.tvAtmosphericValue);
        tvCinematicValue = view.findViewById(R.id.tvCinematicValue);
        tvRhythmicValue = view.findViewById(R.id.tvRhythmicValue);
        tvSongCountValue = view.findViewById(R.id.tvSongCountValue);

        btnGenerate = view.findViewById(R.id.btnGenerate);
        btnResetSliders = view.findViewById(R.id.btnResetSliders);
        btnPlayAll = view.findViewById(R.id.btnPlayAll);
        btnSavePlaylist = view.findViewById(R.id.btnSavePlaylist);

        progressBar = view.findViewById(R.id.progressBar);
        cardResults = view.findViewById(R.id.cardResults);
        recyclerViewResults = view.findViewById(R.id.recyclerViewResults);

        chipEnergetic = view.findViewById(R.id.chipEnergetic);
        chipChill = view.findViewById(R.id.chipChill);
        chipFocus = view.findViewById(R.id.chipFocus);
        chipWorkout = view.findViewById(R.id.chipWorkout);
        chipParty = view.findViewById(R.id.chipParty);
    }

    private void setupListeners() {
        // Slider listeners
        setupSeekBarListener(seekBarHype, tvHypeValue);
        setupSeekBarListener(seekBarAggressive, tvAggressiveValue);
        setupSeekBarListener(seekBarMelodic, tvMelodicValue);
        setupSeekBarListener(seekBarAtmospheric, tvAtmosphericValue);
        setupSeekBarListener(seekBarCinematic, tvCinematicValue);
        setupSeekBarListener(seekBarRhythmic, tvRhythmicValue);

        seekBarSongCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int count = Math.max(5, progress); // Minimum 5 songs
                tvSongCountValue.setText(String.valueOf(count));
                seekBar.setProgress(count);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Reset button
        btnResetSliders.setOnClickListener(v -> resetSliders());

        // Preset chips
        chipEnergetic.setOnClickListener(v -> applyPreset("energetic"));
        chipChill.setOnClickListener(v -> applyPreset("chill"));
        chipFocus.setOnClickListener(v -> applyPreset("focus"));
        chipWorkout.setOnClickListener(v -> applyPreset("workout"));
        chipParty.setOnClickListener(v -> applyPreset("party"));

        // Generate button
        btnGenerate.setOnClickListener(v -> generatePlaylist());

        // Action buttons
        btnPlayAll.setOnClickListener(v -> playAllSongs());
        btnSavePlaylist.setOnClickListener(v -> savePlaylist());
    }

    private void setupSeekBarListener(SeekBar seekBar, TextView valueText) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void resetSliders() {
        seekBarHype.setProgress(50);
        seekBarAggressive.setProgress(50);
        seekBarMelodic.setProgress(50);
        seekBarAtmospheric.setProgress(50);
        seekBarCinematic.setProgress(50);
        seekBarRhythmic.setProgress(50);
    }

    private void applyPreset(String preset) {
        switch (preset.toLowerCase()) {
            case "energetic":
                seekBarHype.setProgress(85);
                seekBarAggressive.setProgress(60);
                seekBarMelodic.setProgress(70);
                seekBarAtmospheric.setProgress(40);
                seekBarCinematic.setProgress(50);
                seekBarRhythmic.setProgress(80);
                etTextQuery.setText("Energetic upbeat high energy music");
                break;
            case "chill":
                seekBarHype.setProgress(30);
                seekBarAggressive.setProgress(20);
                seekBarMelodic.setProgress(70);
                seekBarAtmospheric.setProgress(75);
                seekBarCinematic.setProgress(60);
                seekBarRhythmic.setProgress(40);
                etTextQuery.setText("Chill relaxing calm peaceful music");
                break;
            case "focus":
                seekBarHype.setProgress(40);
                seekBarAggressive.setProgress(20);
                seekBarMelodic.setProgress(60);
                seekBarAtmospheric.setProgress(80);
                seekBarCinematic.setProgress(70);
                seekBarRhythmic.setProgress(30);
                etTextQuery.setText("Focus concentration ambient study music");
                break;
            case "workout":
                seekBarHype.setProgress(90);
                seekBarAggressive.setProgress(70);
                seekBarMelodic.setProgress(50);
                seekBarAtmospheric.setProgress(30);
                seekBarCinematic.setProgress(40);
                seekBarRhythmic.setProgress(85);
                etTextQuery.setText("Intense workout powerful energetic gym music");
                break;
            case "party":
                seekBarHype.setProgress(95);
                seekBarAggressive.setProgress(50);
                seekBarMelodic.setProgress(75);
                seekBarAtmospheric.setProgress(40);
                seekBarCinematic.setProgress(45);
                seekBarRhythmic.setProgress(90);
                etTextQuery.setText("Party dance upbeat fun energetic music");
                break;
        }
    }

    private void generatePlaylist() {
        String query = etTextQuery.getText().toString().trim();

        if (query.isEmpty()) {
            Toast.makeText(getContext(), "Please describe what you want to hear or select a preset",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Collect mood preferences
        Map<String, Integer> moodPrefs = new HashMap<>();
        moodPrefs.put("hype", seekBarHype.getProgress());
        moodPrefs.put("aggressive", seekBarAggressive.getProgress());
        moodPrefs.put("melodic", seekBarMelodic.getProgress());
        moodPrefs.put("atmospheric", seekBarAtmospheric.getProgress());
        moodPrefs.put("cinematic", seekBarCinematic.getProgress());
        moodPrefs.put("rhythmic", seekBarRhythmic.getProgress());

        int songCount = Math.max(5, seekBarSongCount.getProgress());

        // Show loading
        progressBar.setVisibility(View.VISIBLE);
        cardResults.setVisibility(View.GONE);
        btnGenerate.setEnabled(false);

        // Generate recommendations in background thread
        final String finalQuery = query;
        final Map<String, Integer> finalMoodPrefs = moodPrefs;
        final int finalSongCount = songCount;

        executorService.execute(() -> {
            try {
                // Get recommendations (runs in background)
                Set<AIRecommendationEngine.RecommendedSong> recommendations;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    recommendations = aiEngine.getRecommendations(finalQuery,  finalSongCount, new HashSet<>()).stream().collect(Collectors.toSet());
                }else {
                    recommendations = null;
                    return;
                }

                // Update UI on main thread
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerate.setEnabled(true);

                    if (recommendations == null || recommendations.isEmpty()) {
                        Toast.makeText(getContext(),
                                "No songs found matching your criteria. Try adjusting your preferences.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Convert to Song objects
                    generatedSongs.clear();
                    for (AIRecommendationEngine.RecommendedSong recSong : recommendations) {
                        generatedSongs.add(recSong.toSong());
                    }

                    songAdapter.notifyDataSetChanged();
                    cardResults.setVisibility(View.VISIBLE);

                    // Scroll to results
                    if (getView() != null) {
                        getView().post(() -> {
                            cardResults.requestFocus();
                        });
                    }

                    Toast.makeText(getContext(),
                            "✨ Generated " + generatedSongs.size() + " songs for you!",
                            Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                // Handle error on main thread
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerate.setEnabled(true);
                    Toast.makeText(getContext(),
                            "Error generating playlist: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                });
            }
        });
    }

    private void playAllSongs() {
        if (generatedSongs.isEmpty()) {
            return;
        }

        playerController.playQueue(generatedSongs, 0);
        Toast.makeText(getContext(),
                "Playing all generated songs!",
                Toast.LENGTH_SHORT).show();
    }

    private void savePlaylist() {
        if (generatedSongs.isEmpty()) {
            return;
        }
        String playListName = etTextQuery.getText().toString().trim();

        Playlist playList =libraryProvider.getLibraryRepository().createPlaylist("AI Made");
        if (playList == null) {
            Toast.makeText(getContext(),
                    "No playlist name provided",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(),
                "Saving playlist...",
                Toast.LENGTH_SHORT).show();
        for (Song s: generatedSongs) {
            libraryProvider.getLibraryRepository().addSongToPlaylist(playList.getId(),s);
        }


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (aiEngine != null) {
            aiEngine.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}