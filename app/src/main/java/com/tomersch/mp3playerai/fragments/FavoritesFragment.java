package com.tomersch.mp3playerai.fragments;

import android.annotation.SuppressLint;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment to display favorite songs
 */
public class FavoritesFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private SongAdapter songAdapter;
    private List<Song> favoritesList = new ArrayList<>(); // Initialize here!
    private SongAdapter.OnSongClickListener clickListener;
    private FavoritesManager favoritesManager;

    public static FavoritesFragment newInstance() {
        return new FavoritesFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorites, container, false);

        recyclerView = view.findViewById(R.id.rvAllSongs);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // favoritesList already initialized as empty ArrayList
        songAdapter = new SongAdapter(favoritesList, clickListener);
        if (favoritesManager != null) {
            songAdapter.setFavoritesManager(favoritesManager);
        }
        recyclerView.setAdapter(songAdapter);

        updateEmptyState();

        return view;
    }

    public void setSongClickListener(SongAdapter.OnSongClickListener listener) {
        this.clickListener = listener;
        if (songAdapter != null) {
            songAdapter = new SongAdapter(favoritesList, clickListener);
            songAdapter.setFavoritesManager(favoritesManager);
            if (recyclerView != null) {
                recyclerView.setAdapter(songAdapter);
            }
        }
    }

    public void setFavoritesManager(FavoritesManager favoritesManager) {
        this.favoritesManager = favoritesManager;
        if (songAdapter != null) {
            songAdapter.setFavoritesManager(favoritesManager);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateFavorites(List<Song> favorites) {
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

    private void updateEmptyState() {
        if (tvEmptyState != null && recyclerView != null) {
            if (favoritesList == null || favoritesList.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                tvEmptyState.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                tvEmptyState.setVisibility(View.GONE);
            }
        }
    }

    public List<Song> getFavorites() {
        return favoritesList != null ? favoritesList : new ArrayList<>();
    }
}