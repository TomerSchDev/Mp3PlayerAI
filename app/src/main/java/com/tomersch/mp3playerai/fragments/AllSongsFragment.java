package com.tomersch.mp3playerai.fragments;

import android.os.Bundle;
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
import com.tomersch.mp3playerai.utils.FavoritesManager;

import android.util.Log;
import java.util.List;

/**
 * Fragment for All Songs tab
 */
public class AllSongsFragment extends Fragment {

    private static final String TAG = "AllSongsFragment";

    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private List<Song> songList;
    private FavoritesManager favoritesManager;

    private static AllSongsFragment allSongsFragment;

    private AllSongsFragment (){};
    public static AllSongsFragment getInstance()
    {
        if (allSongsFragment == null)
        {
            allSongsFragment = new AllSongsFragment();
        }
        return allSongsFragment;
    }

    private final SongAdapter.OnSongClickListener clickListener = ( position) -> {
        // Call MainActivity's playSong method
        ((com.tomersch.mp3playerai.activities.MainActivity) getActivity()).playSong(songList, position);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_songs, container, false);

        // Use the correct ID from your layout
        recyclerView = view.findViewById(R.id.rvAllSongs);

        if (recyclerView == null) {
            Log.e(TAG, "ERROR: RecyclerView with id 'rvAllSongs' not found in layout!");
            return view;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        if (songList != null) {
            songAdapter = new SongAdapter(songList, clickListener);
            if (favoritesManager != null) {
                Log.d(TAG, "Setting FavoritesManager in adapter");
                songAdapter.setFavoritesManager(favoritesManager);
            } else {
                Log.w(TAG, "FavoritesManager is null in onCreateView");
            }
            recyclerView.setAdapter(songAdapter);
        }

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
     * Set the song list
     */
    public void setSongList(List<Song> songs) {
        this.songList = songs;
        if (songAdapter != null && recyclerView != null) {
            songAdapter = new SongAdapter(songList, clickListener);
            if (favoritesManager != null) {
                songAdapter.setFavoritesManager(favoritesManager);
            }
            recyclerView.setAdapter(songAdapter);
        }
    }

    public static AllSongsFragment newInstance() {
        return new AllSongsFragment();
    }
}