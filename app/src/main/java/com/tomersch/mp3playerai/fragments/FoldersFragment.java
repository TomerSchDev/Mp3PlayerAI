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
import com.tomersch.mp3playerai.activities.MainActivity;
import com.tomersch.mp3playerai.adapters.FolderAdapter;
import com.tomersch.mp3playerai.models.MusicFolder;
import com.tomersch.mp3playerai.models.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment for Folders tab
 */
public class FoldersFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private FolderAdapter folderAdapter;
    private List<Song> songList;

    public static FoldersFragment newInstance() {
        if (foldersFragment == null) {
            foldersFragment = new FoldersFragment();
        }
        return foldersFragment;
    }
    private FoldersFragment() {};
    private static FoldersFragment foldersFragment;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_folders, container, false);

        // Use the correct IDs from your layout
        recyclerView = view.findViewById(R.id.rvFolders);
        tvEmptyState = view.findViewById(R.id.tvESFolders);

        if (recyclerView == null) {
            return view;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        if (songList != null) {
            List<MusicFolder> folderItems = groupSongsByFolder(songList);
            if (folderItems.isEmpty()) {
                if (tvEmptyState != null) {
                    tvEmptyState.setVisibility(View.VISIBLE);
                }
                recyclerView.setVisibility(View.GONE);
            } else {
                if (tvEmptyState != null) {
                    tvEmptyState.setVisibility(View.GONE);
                }
                recyclerView.setVisibility(View.VISIBLE);
                folderAdapter = new FolderAdapter(folderItems, this::onFolderClick);
                recyclerView.setAdapter(folderAdapter);
            }
        }

        return view;
    }

    /**
     * Set the song list
     */
    public void setSongList(List<Song> songs) {
        this.songList = songs;
        if (recyclerView != null) {
            List<MusicFolder> folderItems = groupSongsByFolder(songs);
            if (folderItems.isEmpty()) {
                if (tvEmptyState != null) {
                    tvEmptyState.setVisibility(View.VISIBLE);
                }
                recyclerView.setVisibility(View.GONE);
            } else {
                if (tvEmptyState != null) {
                    tvEmptyState.setVisibility(View.GONE);
                }
                recyclerView.setVisibility(View.VISIBLE);
                folderAdapter = new FolderAdapter(folderItems, this::onFolderClick);
                recyclerView.setAdapter(folderAdapter);
            }
        }
    }

    /**
     * Group songs by folder path
     */
    private List<MusicFolder> groupSongsByFolder(List<Song> songs) {
        Map<String, List<Song>> folderMap = new HashMap<>();

        for (Song song : songs) {
            String path = song.getPath();
            String folderPath = path.substring(0, path.lastIndexOf("/"));
            String folderName = folderPath.substring(folderPath.lastIndexOf("/") + 1);

            if (!folderMap.containsKey(folderPath)) {
                folderMap.put(folderPath, new ArrayList<>());
            }
            folderMap.get(folderPath).add(song);
        }

        List<MusicFolder> folderItems = new ArrayList<>();
        for (Map.Entry<String, List<Song>> entry : folderMap.entrySet()) {
            String folderPath = entry.getKey();
            String folderName = folderPath.substring(folderPath.lastIndexOf("/") + 1);
            List<Song> songsInFolder = entry.getValue();
            folderItems.add(new MusicFolder(folderName, folderPath, songsInFolder.size()));
        }

        return folderItems;
    }

    /**
     * Handle folder click
     */
    private void onFolderClick(MusicFolder musicFolder) {
        // Filter songs that belong to this folder
        List<Song> songsInFolder = new ArrayList<>();
        for (Song song : songList) {
            String songFolderPath = song.getPath().substring(0, song.getPath().lastIndexOf("/"));
            if (songFolderPath.equals(musicFolder.getFolderPath())) {
                songsInFolder.add(song);
            }
        }

        // Create subtitle showing song count
        String subtitle = songsInFolder.size() + " song" +
                (songsInFolder.size() != 1 ? "s" : "");

        // Use ListContainerFragment to show songs in this folder
        ListContainerFragment containerFragment = ListContainerFragment.newInstance(
                new ArrayList<>(songsInFolder),
                musicFolder.getFolderName(),
                subtitle
        );

        // Use child fragment manager to add to the folder_container
        getChildFragmentManager().beginTransaction()
                .replace(R.id.folder_container, containerFragment)
                .addToBackStack(null)
                .commit();
    }
}