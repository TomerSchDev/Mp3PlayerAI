package com.tomersch.mp3playerai.fragments;

import android.content.Context;
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
import com.tomersch.mp3playerai.adapters.FolderAdapter;
import com.tomersch.mp3playerai.models.MusicFolder;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.player.LibraryProvider;
import com.tomersch.mp3playerai.utils.LibraryRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Folders tab - reads from LibraryRepository, plays via PlayerController.
 */
public class FoldersFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmptyState;

    private LibraryRepository repo;

    private List<Song> allSongs = new ArrayList<>();

    // Keep reference so removeListener removes same instance
    private final Runnable repoListener = this::refreshFromRepository;

    public FoldersFragment() {}

    public static FoldersFragment newInstance() {
        return new FoldersFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);


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
        View view = inflater.inflate(R.layout.fragment_folders, container, false);

        recyclerView = view.findViewById(R.id.rvFolders);
        tvEmptyState = view.findViewById(R.id.tvESFolders);

        if (recyclerView == null) return view;

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initial load
        allSongs = (repo != null) ? repo.getAllSongs() : new ArrayList<>();
        renderFolders(allSongs);

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
        allSongs = repo.getAllSongs();
        if (allSongs == null) allSongs = new ArrayList<>();
        renderFolders(allSongs);
    }

    private void renderFolders(List<Song> songs) {
        if (recyclerView == null) return;

        List<MusicFolder> folderItems = groupSongsByFolder(songs);

        boolean empty = folderItems.isEmpty();
        if (tvEmptyState != null) tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            recyclerView.setAdapter(new FolderAdapter(folderItems, this::onFolderClick));
        }
    }

    /**
     * Group songs by folder path
     */
    private List<MusicFolder> groupSongsByFolder(List<Song> songs) {
        Map<String, List<Song>> folderMap = new HashMap<>();

        for (Song song : songs) {
            String path = song.getPath();
            if (path == null) continue;
            int lastSlash = path.lastIndexOf("/");
            if (lastSlash <= 0) continue;
            String folderPath = path.substring(0, lastSlash);

            if (!folderMap.containsKey(folderPath)) {
                folderMap.put(folderPath, new ArrayList<>());
            }
            folderMap.computeIfAbsent(folderPath, k -> new ArrayList<>()).add(song);
        }
        List<MusicFolder> folderItems = new ArrayList<>();
        for (Map.Entry<String, List<Song>> entry : folderMap.entrySet()) {
            String folderPath = entry.getKey();
            int lastSlash = folderPath.lastIndexOf("/");
            String folderName = (lastSlash >= 0) ? folderPath.substring(lastSlash + 1) : folderPath;

            List<Song> songsInFolder = entry.getValue();
            folderItems.add(new MusicFolder(folderName, folderPath, songsInFolder.size()));
        }

        return folderItems;
    }

    /**
     * Handle folder click
     */
    private void onFolderClick(MusicFolder musicFolder) {
        if (musicFolder == null || allSongs == null) return;

        // Filter songs in this folder
        List<Song> songsInFolder = new ArrayList<>();
        for (Song song : allSongs) {
            String path = song.getPath();
            int lastSlash = path.lastIndexOf("/");
            if (lastSlash <= 0) continue;

            String songFolderPath = path.substring(0, lastSlash);
            if (songFolderPath.equals(musicFolder.getFolderPath())) {
                songsInFolder.add(song);
            }
        }

        String subtitle = songsInFolder.size() + " song" + (songsInFolder.size() != 1 ? "s" : "");

        ListContainerFragment containerFragment = ListContainerFragment.newInstance(
                new ArrayList<>(songsInFolder),
                musicFolder.getFolderName(),
                subtitle
        );

        getChildFragmentManager().beginTransaction()
                .replace(R.id.folder_container, containerFragment)
                .addToBackStack(null)
                .commit();
    }
}
