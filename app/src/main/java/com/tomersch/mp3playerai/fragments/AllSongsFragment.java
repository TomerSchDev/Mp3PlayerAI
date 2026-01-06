package com.tomersch.mp3playerai.fragments;

import android.annotation.SuppressLint;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment to display all songs
 */
public class AllSongsFragment extends Fragment {

    private static final String TAG = "AllSongsFragment";
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private List<Song> songList = new ArrayList<>(); // Initialize here!
    private SongAdapter.OnSongClickListener clickListener;
    private FavoritesManager favoritesManager;

    public static AllSongsFragment newInstance() {
        return new AllSongsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_songs, container, false);

        recyclerView = view.findViewById(R.id.rvAllSongs);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // songList already initialized as empty ArrayList
        songAdapter = new SongAdapter(songList, clickListener);
        if (favoritesManager != null) {
            songAdapter.setFavoritesManager(favoritesManager);
        }
        recyclerView.setAdapter(songAdapter);

        return view;
    }

    public void setSongClickListener(SongAdapter.OnSongClickListener listener) {
        this.clickListener = listener;
        if (songAdapter != null) {
            songAdapter = new SongAdapter(songList, clickListener);
            songAdapter.setFavoritesManager(favoritesManager);
            if (recyclerView != null) {
                recyclerView.setAdapter(songAdapter);
            }
        }
    }

    public void setFavoritesManager(FavoritesManager favoritesManager) {
        Log.d(TAG, "setFavoritesManager called, manager: " + (favoritesManager != null ? "exists" : "NULL"));
        this.favoritesManager = favoritesManager;
        if (songAdapter != null) {
            Log.d(TAG, "Setting FavoritesManager on adapter");
            songAdapter.setFavoritesManager(favoritesManager);
        } else {
            Log.w(TAG, "songAdapter is NULL, will set FavoritesManager later");
        }
    }


    public void updateSongs(List<Song> songs) {
        if (songList == null) {
            songList = new ArrayList<>();
        }
        songList.clear();
        if (songs != null) {
            songList.addAll(songs);
        }
        if (songAdapter != null) {
            // Make sure adapter has favorites manager before notifying
            if (favoritesManager != null) {
                songAdapter.setFavoritesManager(favoritesManager);
            }
            songAdapter.notifyDataSetChanged();
        }
    }

    public List<Song> getSongs() {
        return songList != null ? songList : new ArrayList<>();
    }
}