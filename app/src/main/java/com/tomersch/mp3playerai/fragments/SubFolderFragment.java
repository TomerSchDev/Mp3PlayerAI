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
import com.tomersch.mp3playerai.activities.MainActivity;
import com.tomersch.mp3playerai.adapters.SongAdapter;
import com.tomersch.mp3playerai.models.Song;

import java.util.ArrayList;
import java.util.List;

public class SubFolderFragment extends Fragment {
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private List<Song> folderSongs;
    private String folderName;
    private SubFolderFragment() {};
    public static SubFolderFragment newInstance(ArrayList<Song> songs, String name) {
        SubFolderFragment fragment = new SubFolderFragment();
        Bundle args = new Bundle();
        args.putSerializable("songs", songs);
        args.putString("name", name);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            folderSongs = (ArrayList<Song>) getArguments().getSerializable("songs");
            folderName = getArguments().getString("name");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_songs, container, false);

        recyclerView = view.findViewById(R.id.rvAllSongs);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        setupRecyclerView();

        return view;
    }

    // Inside SubFolderFragment.java -> setupRecyclerView()
    private void setupRecyclerView() {
        if (folderSongs != null) {
            songAdapter = new SongAdapter(folderSongs, position -> {
                // Call the unified play method in MainActivity
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).playSong(folderSongs, position);
                }
            });

            // Set the favorites manager so hearts work in subfolders
            if (getActivity() instanceof MainActivity) {
                songAdapter.setFavoritesManager(((MainActivity) getActivity()).getFavoritesManager());
            }

            recyclerView.setAdapter(songAdapter);
        }
    }
}