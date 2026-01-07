package com.tomersch.mp3playerai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.adapters.SongAdapter;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.utils.FavoritesManager;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fragment to display favorite songs
 */
public class FavoritesFragment extends Fragment {

    private static final String TAG = "FavoritesFragment";

    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private SongAdapter songAdapter;
    private List<Song> favoritesList;
    private FavoritesManager favoritesManager;
    private static FavoritesFragment favoritesFragment;
    public static FavoritesFragment getInstance() {
        if (favoritesFragment == null) {
            favoritesFragment = new FavoritesFragment();
        }
        return favoritesFragment;
    }
    private FavoritesFragment() {};
    private final SongAdapter.OnSongClickListener clickListener = ( position) -> {
        // Call MainActivity's playSong method
        ((com.tomersch.mp3playerai.activities.MainActivity) Objects.requireNonNull(getActivity())).playSong(favoritesList, position);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorites, container, false);

        // Use the correct IDs from your layout
        recyclerView = view.findViewById(R.id.rvFavorite);
        tvEmptyState = view.findViewById(R.id.tvEVFavorite);

        // Critical null check BEFORE using recyclerView
        if (recyclerView == null) {
            Log.e(TAG, "ERROR: RecyclerView with id 'rvFavorite' not found in layout!");
            return view;
        }

        if (tvEmptyState == null) {
            Log.e(TAG, "ERROR: TextView with id 'tvEVFavorite' not found in layout!");
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize favorites list as empty
        favoritesList = new ArrayList<>();

        songAdapter = new SongAdapter(favoritesList, clickListener);
        if (favoritesManager != null) {
            songAdapter.setFavoritesManager(favoritesManager);
        }
        recyclerView.setAdapter(songAdapter);

        updateEmptyState();

        return view;
    }

    /**
     * Set FavoritesManager - called from MainActivity
     */
    public void setFavoritesManager(FavoritesManager manager) {
        Log.d(TAG, "setFavoritesManager called, manager is " + (manager != null ? "not null" : "null"));
        this.favoritesManager = manager;
        if (songAdapter != null) {
            songAdapter.setFavoritesManager(manager);
        }
    }

    /**
     * Update the list with favorite songs
     */
    public void updateFavorites(List<Song> favorites) {
        Log.d(TAG, "updateFavorites called with " + (favorites != null ? favorites.size() : 0) + " songs");
        if (favoritesList == null) {
            favoritesList = new ArrayList<>();
        }
        favoritesList.clear();
        if (favorites != null) {
            favoritesList.addAll(favorites);
        }
        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    /**
     * Show/hide empty state based on favorites list
     */
    private void updateEmptyState() {
        if (tvEmptyState == null || recyclerView == null) {
            return;
        }

        if (favoritesList == null || favoritesList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    public static FavoritesFragment newInstance() {
        return new FavoritesFragment();
    }
}