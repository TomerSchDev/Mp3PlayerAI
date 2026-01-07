package com.tomersch.mp3playerai.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.activities.MainActivity;
import com.tomersch.mp3playerai.adapters.PlaylistAdapter;
import com.tomersch.mp3playerai.models.Playlist;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.utils.PlaylistManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for Playlists tab
 */
public class PlaylistsFragment extends Fragment {

    private RecyclerView rvPlaylists;
    private FloatingActionButton fabCreatePlaylist;
    private LinearLayout emptyState;
    private PlaylistAdapter adapter;
    private PlaylistManager playlistManager;
    private static PlaylistsFragment instance;
    private PlaylistsFragment (){};
    public static PlaylistsFragment newInstance() {
        if (instance == null) {
            instance = new PlaylistsFragment();
        }
        return instance;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlists, container, false);

        playlistManager = ((MainActivity) getActivity()).getPlaylistManager();

        rvPlaylists = view.findViewById(R.id.rvPlaylists);
        fabCreatePlaylist = view.findViewById(R.id.fabCreatePlaylist);
        emptyState = view.findViewById(R.id.emptyState);

        rvPlaylists.setLayoutManager(new LinearLayoutManager(getContext()));

        loadPlaylists();

        fabCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());

        // Listen for playlist changes
        playlistManager.addListener(this::loadPlaylists);

        return view;
    }

    private void loadPlaylists() {
        List<Playlist> playlists = playlistManager.getAllPlaylists();

        if (playlists.isEmpty()) {
            rvPlaylists.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rvPlaylists.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);

            adapter = new PlaylistAdapter(playlists, this::openPlaylist);
            adapter.setOnPlaylistLongClickListener(this::showPlaylistOptions);
            rvPlaylists.setAdapter(adapter);
        }
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_create_playlist_title);

        final EditText input = new EditText(getContext());
        input.setHint(R.string.dialog_playlist_name_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.dialog_create, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                playlistManager.createPlaylist(name);
                Toast.makeText(getContext(), getString(R.string.toast_playlist_created, name),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.toast_playlist_name_required,
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void openPlaylist(Playlist playlist) {
        // Get all songs from MainActivity
        List<Song> allSongs = ((MainActivity) getActivity()).getSongList();

        // Filter songs that are in this playlist
        List<Song> playlistSongs = new ArrayList<>();
        for (Song song : allSongs) {
            if (playlist.containsSong(song.getPath())) {
                playlistSongs.add(song);
            }
        }

        // Create subtitle
        String subtitle = playlistSongs.size() + " song" + (playlistSongs.size() != 1 ? "s" : "");

        // Use ListContainerFragment
        ListContainerFragment containerFragment = ListContainerFragment.newInstance(
                new ArrayList<>(playlistSongs),
                playlist.getName(),
                subtitle
        );

        getChildFragmentManager().beginTransaction()
                .replace(R.id.playlist_container, containerFragment)
                .addToBackStack(null)
                .commit();
    }

    private void showPlaylistOptions(Playlist playlist) {
        String[] options = {
                getString(R.string.dialog_option_rename),
                getString(R.string.dialog_option_delete)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(playlist.getName());
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Rename
                    showRenamePlaylistDialog(playlist);
                    break;
                case 1: // Delete
                    showDeleteConfirmation(playlist);
                    break;
            }
        });
        builder.show();
    }

    private void showRenamePlaylistDialog(Playlist playlist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_rename_playlist_title);

        final EditText input = new EditText(getContext());
        input.setText(playlist.getName());
        input.setHint(R.string.dialog_playlist_name_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.dialog_rename, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                playlistManager.renamePlaylist(playlist.getId(), newName);
                Toast.makeText(getContext(), getString(R.string.toast_playlist_renamed, newName),
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showDeleteConfirmation(Playlist playlist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_delete_playlist_title);
        builder.setMessage(getString(R.string.dialog_delete_playlist_message, playlist.getName()));

        builder.setPositiveButton(R.string.dialog_delete, (dialog, which) -> {
            playlistManager.deletePlaylist(playlist.getId());
            Toast.makeText(getContext(), getString(R.string.toast_playlist_deleted, playlist.getName()),
                    Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (playlistManager != null) {
            playlistManager.removeListener(this::loadPlaylists);
        }
    }
}