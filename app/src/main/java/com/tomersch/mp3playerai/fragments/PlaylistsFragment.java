package com.tomersch.mp3playerai.fragments;

import static com.tomersch.mp3playerai.utils.UiUtils.dp;

import android.app.AlertDialog;
import android.content.Context;
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
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.adapters.PlaylistAdapter;
import com.tomersch.mp3playerai.models.Playlist;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.player.LibraryProvider;
import com.tomersch.mp3playerai.utils.LibraryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fragment for Playlists tab (LibraryRepository-backed).
 */
public class PlaylistsFragment extends Fragment {

    private RecyclerView rvPlaylists;
    private LinearLayout emptyState;

    private LibraryRepository repo;

    // Currently open playlist (inside playlist_container)
    private String currentOpenPlaylistId;

    // Keep reference so removeListener() removes the same instance
    private final Runnable repoListener = () -> {
        loadPlaylists();
        refreshCurrentPlaylistIfOpen();
    };

    public PlaylistsFragment() {}

    public static PlaylistsFragment newInstance() {
        return new PlaylistsFragment();
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
        View view = inflater.inflate(R.layout.fragment_playlists, container, false);

        rvPlaylists = view.findViewById(R.id.rvPlaylists);
        FloatingActionButton fabGlobal =view.findViewById(R.id.fabCreatePlaylist);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fabGlobal.getLayoutParams();
        lp.bottomMargin = dp(this.requireContext(),16) + dp(this.requireContext(),64); // 64dp mini player height
        fabGlobal.setLayoutParams(lp);
        fabGlobal.setOnClickListener(v -> showCreatePlaylistDialog());

        emptyState = view.findViewById(R.id.emptyState);

        rvPlaylists.setLayoutManager(new LinearLayoutManager(getContext()));

        loadPlaylists();


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
    private void showCreatePlaylistDialog() {
        if (getContext() == null || repo == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_create_playlist_title);

        final EditText input = new EditText(getContext());
        input.setHint(R.string.dialog_playlist_name_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.dialog_create, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                repo.createPlaylist(name);
                Toast.makeText(getContext(),
                        getString(R.string.toast_playlist_created, name),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(),
                        R.string.toast_playlist_name_required,
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }
    private void loadPlaylists() {
        if (repo == null) return;

        List<Playlist> playlists = repo.getPlaylists();

        if (playlists == null || playlists.isEmpty()) {
            rvPlaylists.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        rvPlaylists.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        PlaylistAdapter adapter = new PlaylistAdapter(playlists, this::openPlaylist);
        adapter.setOnPlaylistLongClickListener(this::showPlaylistOptions);
        rvPlaylists.setAdapter(adapter);
    }


    private void openPlaylist(Playlist playlist) {
        openPlaylist(playlist, true); // user click => backstack
    }
    private void openPlaylist(Playlist playlist,boolean addToBackStack) {
        if (playlist == null || repo == null) return;
        currentOpenPlaylistId = playlist.getId();

        List<Song> playlistSongs = repo.getPlaylistSongs(playlist.getId());
        String subtitle = playlistSongs.size() + " song" + (playlistSongs.size() != 1 ? "s" : "");

        ListContainerFragment frag = ListContainerFragment.newInstanceForPlaylist(
                new ArrayList<>(playlistSongs),
                playlist.getName(),
                subtitle,
                playlist.getId()
        );

        FragmentTransaction tx = getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.playlist_container, frag);

        if (addToBackStack) tx.addToBackStack(null);

        tx.commit();
        }


        /**
         * Refresh open playlist view if playlists changed.
         */
        private void refreshCurrentPlaylistIfOpen() {
            if (currentOpenPlaylistId == null) return;

            LibraryRepository repo = LibraryRepository.getInstance(requireContext());
            Playlist playlist = repo.getPlaylist(currentOpenPlaylistId);
            if (playlist == null) return;

            Fragment current = getChildFragmentManager().findFragmentById(R.id.playlist_container);
            if (current instanceof ListContainerFragment) {
                // refresh WITHOUT adding another back stack entry
                openPlaylist(playlist, false);
            } else {
                currentOpenPlaylistId = null;
            }
        }


    @Nullable
    private Playlist findPlaylistById(@NonNull String id) {
        if (repo == null) return null;
        List<Playlist> playlists = repo.getPlaylists();
        if (playlists == null) return null;

        for (Playlist p : playlists) {
            if (p != null && id.equals(p.getId())) return p;
        }
        return null;
    }

    private void showPlaylistOptions(Playlist playlist) {
        if (getContext() == null) return;

        String[] options = {
                getString(R.string.dialog_option_rename),
                getString(R.string.dialog_option_delete)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(playlist.getName());
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) showRenamePlaylistDialog(playlist);
            else if (which == 1) showDeleteConfirmation(playlist);
        });
        builder.show();
    }

    private void showRenamePlaylistDialog(Playlist playlist) {
        if (getContext() == null || repo == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_rename_playlist_title);

        final EditText input = new EditText(getContext());
        input.setText(playlist.getName());
        input.setHint(R.string.dialog_playlist_name_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.dialog_rename, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                repo.renamePlaylist(playlist.getId(), newName);
                Toast.makeText(getContext(),
                        getString(R.string.toast_playlist_renamed, newName),
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteConfirmation(Playlist playlist) {
        if (getContext() == null || repo == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_delete_playlist_title);
        builder.setMessage(getString(R.string.dialog_delete_playlist_message, playlist.getName()));

        builder.setPositiveButton(R.string.dialog_delete, (dialog, which) -> {
            repo.deletePlaylist(playlist.getId());
            Toast.makeText(getContext(),
                    getString(R.string.toast_playlist_deleted, playlist.getName()),
                    Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        currentOpenPlaylistId = null;
    }
}
