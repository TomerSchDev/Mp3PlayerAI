package com.tomersch.mp3playerai.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.models.Playlist;
import com.tomersch.mp3playerai.models.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for showing playlist dialogs
 */
public class PlaylistDialogHelper {

    /**
     * Show dialog to add a song to a playlist (from All Songs, Favorites, Folders)
     */
    public static void showAddToPlaylistDialog(Context context, Song song, LibraryRepository repo) {
        List<Playlist> playlists = repo.getPlaylists();

        if (playlists.isEmpty()) {
            Toast.makeText(context, R.string.toast_no_playlists_create_first, Toast.LENGTH_SHORT).show();
            return;
        }

        // Build list of playlist names
        String[] playlistNames = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            Playlist playlist = playlists.get(i);
            int songCount = playlist.getSongCount();
            boolean alreadyInPlaylist = playlist.containsSong(song.getPath());

            playlistNames[i] = playlist.getName() + " (" + songCount + " songs)" +
                    (alreadyInPlaylist ? " ✓" : "");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.dialog_add_to_playlist_title, song.getTitle()));

        builder.setItems(playlistNames, (dialog, which) -> {
            Playlist selectedPlaylist = playlists.get(which);

            if (selectedPlaylist.containsSong(song.getPath())) {
                // Already in playlist -> ask if remove
                new AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_remove_from_playlist_title)
                        .setMessage(context.getString(R.string.dialog_remove_from_playlist_message,
                                song.getTitle(), selectedPlaylist.getName()))
                        .setPositiveButton(R.string.dialog_remove, (d, w) -> {
                            repo.removeSongFromPlaylist(selectedPlaylist.getId(), song);
                            Toast.makeText(context,
                                    context.getString(R.string.toast_song_removed_from_playlist,
                                            song.getTitle(), selectedPlaylist.getName()),
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .show();
            } else {
                repo.addSongToPlaylist(selectedPlaylist.getId(), song);
                Toast.makeText(context,
                        context.getString(R.string.toast_song_added_to_playlist,
                                song.getTitle(), selectedPlaylist.getName()),
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Show dialog to add/remove a song when already viewing a playlist.
     * Current playlist shown first, then other playlists.
     */
    public static void showAddToPlaylistDialogInContext(
            Context context,
            Song song,
            LibraryRepository repo,
            String currentPlaylistId
    ) {
        List<Playlist> allPlaylists = repo.getPlaylists();

        if (allPlaylists == null ) {
            Toast.makeText(context, R.string.toast_no_playlists_create_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Playlist currentPlaylist = repo.getPlaylist(currentPlaylistId);
        List<Playlist> sortedPlaylists = new ArrayList<>();
        List<String> playlistLabels = new ArrayList<>();

        // Current playlist first
        boolean inCurrent = currentPlaylist.containsSong(song.getPath());
        sortedPlaylists.add(currentPlaylist);
        playlistLabels.add((inCurrent ? "➖ Remove from " : "➕ Add to ")
                + currentPlaylist.getName() + " (this playlist)");

        // Separator (click does nothing)
        sortedPlaylists.add(null);
        playlistLabels.add("--- Other Playlists ---");

        // Other playlists
        for (Playlist playlist : allPlaylists) {
            if (!playlist.getId().equals(currentPlaylistId)) {
                sortedPlaylists.add(playlist);
                int songCount = playlist.getSongCount();
                boolean alreadyInPlaylist = playlist.containsSong(song.getPath());
                playlistLabels.add(playlist.getName() + " (" + songCount + " songs)" +
                        (alreadyInPlaylist ? " ✓" : ""));
            }
        }

        String[] labels = playlistLabels.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.dialog_add_to_playlist_title, song.getTitle()));

        builder.setItems(labels, (dialog, which) -> {
            // separator index is 1
            if (which == 1) return;

            Playlist selected = sortedPlaylists.get(which);
            if (selected == null) return;

            boolean alreadyIn = selected.containsSong(song.getPath());

            if (alreadyIn) {
                if (selected.getId().equals(currentPlaylistId)) {
                    // current playlist: remove directly
                    repo.removeSongFromPlaylist(selected.getId(), song);
                    Toast.makeText(context,
                            context.getString(R.string.toast_song_removed_from_playlist,
                                    song.getTitle(), selected.getName()),
                            Toast.LENGTH_SHORT).show();
                } else {
                    // other playlist: confirm
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.dialog_remove_from_playlist_title)
                            .setMessage(context.getString(R.string.dialog_remove_from_playlist_message,
                                    song.getTitle(), selected.getName()))
                            .setPositiveButton(R.string.dialog_remove, (d, w) -> {
                                repo.removeSongFromPlaylist(selected.getId(), song);
                                Toast.makeText(context,
                                        context.getString(R.string.toast_song_removed_from_playlist,
                                                song.getTitle(), selected.getName()),
                                        Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(R.string.dialog_cancel, null)
                            .show();
                }
            } else {
                repo.addSongToPlaylist(selected.getId(), song);
                Toast.makeText(context,
                        context.getString(R.string.toast_song_added_to_playlist,
                                song.getTitle(), selected.getName()),
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

}
