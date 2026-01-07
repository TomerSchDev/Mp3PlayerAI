package com.tomersch.mp3playerai.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
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
import com.tomersch.mp3playerai.player.LibraryProvider;
import com.tomersch.mp3playerai.player.PlayerController;
import com.tomersch.mp3playerai.utils.LibraryRepository;
import com.tomersch.mp3playerai.utils.PlaylistDialogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Favorites tab - reads from LibraryRepository, plays via PlayerController.
 */
public class FavoritesFragment extends Fragment {

    private static final String TAG = "FavoritesFragment";

    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private SongAdapter songAdapter;

    private PlayerController playerController;
    private LibraryRepository repo;

    private List<Song> favoritesList = new ArrayList<>();

    // Keep a reference so removeListener() removes the same instance
    private final Runnable repoListener = this::refreshFromRepository;

    public FavoritesFragment() {}

    public static FavoritesFragment newInstance() {
        return new FavoritesFragment();
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
            repo = ((LibraryProvider) context).getLibraryRepository();
        } else {
            throw new IllegalStateException("Host activity must implement LibraryProvider");
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_favorites, container, false);

        recyclerView = view.findViewById(R.id.rvFavorite);
        tvEmptyState = view.findViewById(R.id.tvEVFavorite);

        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView with id 'rvFavorite' not found in layout!");
            return view;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initial load
        favoritesList = (repo != null) ? repo.getFavoriteSongs() : new ArrayList<>();

        SongAdapter.OnSongClickListener clickListener = (position) -> {
            if (playerController == null) return;
            if (favoritesList == null || favoritesList.isEmpty()) return;
            if (position < 0 || position >= favoritesList.size()) return;
            playerController.playQueue(favoritesList, position);
        };

        songAdapter = new SongAdapter(favoritesList, clickListener);

        // Key: connect adapter to repository for favorite toggles
        songAdapter.setRepository(repo);

        // Add-to-playlist
        songAdapter.setOnAddToPlaylistListener(song -> {
            if (getContext() == null || repo == null) return;
            PlaylistDialogHelper.showAddToPlaylistDialog(getContext(), song, repo);
        });

        recyclerView.setAdapter(songAdapter);

        updateEmptyState();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (repo != null) repo.addListener(repoListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (repo != null) repo.removeListener(repoListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFromRepository();
    }

    private void refreshFromRepository() {
        if (repo == null) return;

        List<Song> updated = repo.getFavoriteSongs();
        if (updated == null) updated = new ArrayList<>();

        favoritesList = updated;

        if (songAdapter != null) {
            songAdapter.updateSongs(favoritesList);
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        if (tvEmptyState == null || recyclerView == null) return;

        boolean empty = (favoritesList == null || favoritesList.isEmpty());
        tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
