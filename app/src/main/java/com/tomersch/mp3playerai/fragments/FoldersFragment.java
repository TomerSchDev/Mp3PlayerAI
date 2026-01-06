package com.tomersch.mp3playerai.fragments;

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
import com.tomersch.mp3playerai.activities.MainActivity;
import com.tomersch.mp3playerai.adapters.FolderAdapter;
import com.tomersch.mp3playerai.models.MusicFolder;
import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fragment for Folders tab
 */
public class FoldersFragment extends Fragment {
    private RecyclerView rvFolders;
    private FolderAdapter adapter;

    public static FoldersFragment newInstance() { return new FoldersFragment(); }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_folders, container, false);
        rvFolders = view.findViewById(R.id.rvFolders);
        rvFolders.setLayoutManager(new LinearLayoutManager(getContext()));

        // This would be called once your MainActivity loads the songs
        loadFolders();
        return view;
    }

    private void loadFolders() {
        // 1. Get all songs from your Cache or MainActivity
        Log.d("FoldersFragment", "loadFolders called");
        List<Song> allSongs = ((MainActivity)getActivity()).getSongList();
        if (allSongs == null)
        {
            Log.e("FoldersFragment", "allSongs is null");
            return;
        }


        // 2. Map to group by parent path
        final Map<String, List<Song>> groups = new HashMap<>();
        for (Song s : allSongs) {
            if (s == null) continue;
            File file = new File(s.getPath());
            String parent = file.getParent();
            if (!groups.containsKey(parent)) groups.put(parent, new ArrayList<>());
            Objects.requireNonNull(groups.get(parent)).add(s);
        }

        // 3. Convert map to list of MusicFolder objects
        List<MusicFolder> folderList = new ArrayList<>();
        for (String path : groups.keySet()) {
            String name = path.substring(path.lastIndexOf("/") + 1);
            folderList.add(new MusicFolder(name, path, groups.get(path).size()));
        }

        adapter = new FolderAdapter(folderList, folder -> {
            // We already have the list of songs for this folder in our 'groups' map
            // Make sure 'groups' is accessible here
            List<Song> songsInFolder = groups.get(folder.getFolderPath());

            if (songsInFolder != null) {
                // Pass the already calculated list to the detail fragment
                SubFolderFragment detailFragment = SubFolderFragment.newInstance(
                        new ArrayList<>(songsInFolder),
                        folder.getFolderName()
                );

                getChildFragmentManager().beginTransaction()
                        .replace(R.id.folder_container, detailFragment)
                        .addToBackStack(null)
                        .commit();
            }
        });
        rvFolders.setAdapter(adapter);
    }
}