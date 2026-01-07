package com.tomersch.mp3playerai.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
 * All Songs tab - reads from LibraryRepository, controls playback via PlayerController.
 */
public class AllSongsFragment extends Fragment {

    private static final String TAG = "AllSongsFragment";

    private RecyclerView recyclerView;
    private SongAdapter songAdapter;

    private PlayerController playerController;
    private LibraryRepository repo;

    private List<Song> songList = new ArrayList<>();

    public AllSongsFragment() {}

    public static AllSongsFragment newInstance() {
        return new AllSongsFragment();
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
        View view = inflater.inflate(R.layout.fragment_all_songs, container, false);

        recyclerView = view.findViewById(R.id.rvAllSongs);
        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView with id 'rvAllSongs' not found in layout!");
            return view;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initial data
        songList = repo != null ? repo.getAllSongs() : new ArrayList<>();

        // Click -> play queue
        SongAdapter.OnSongClickListener clickListener = (position) -> {
            if (playerController == null) return;
            if (songList == null || songList.isEmpty()) return;
            if (position < 0 || position >= songList.size()) return;
            playerController.playQueue(songList, position);
        };

        songAdapter = new SongAdapter(songList, clickListener);
        songAdapter.setRepository(repo);
        songAdapter.setOnAddToPlaylistListener(song ->
                        PlaylistDialogHelper.showAddToPlaylistDialog(getContext(), song, repo));


        recyclerView.setAdapter(songAdapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFromRepository();
    }

    private void refreshFromRepository() {
        if (repo == null) return;

        List<Song> updated = repo.getAllSongs();
        if (updated == null) updated = new ArrayList<>();

        songList = updated;

        if (songAdapter != null) {
            // simplest safe refresh (works with any adapter)
            songAdapter.updateSongs(songList); // <-- you likely don't have this yet
            // If you don't have updateSongs(...), use the fallback below.
        }
    }
}
