package com.tomersch.mp3playerai.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.utils.LibraryRepository;

import java.util.List;
import java.util.Locale;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    private static final String TAG = "SongAdapter";

    private final List<Song> songList;
    private final OnSongClickListener listener;

    private OnAddToPlaylistListener addToPlaylistListener;

    // NEW: single source of truth
    private LibraryRepository repo;

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    public interface OnAddToPlaylistListener {
        void onAddToPlaylist(Song song);
    }

    public SongAdapter(List<Song> songList, OnSongClickListener listener) {
        this.songList = songList;
        this.listener = listener;
    }

    public void setRepository(LibraryRepository repo) {
        this.repo = repo;
        notifyDataSetChanged();
    }

    public void setOnAddToPlaylistListener(OnAddToPlaylistListener listener) {
        this.addToPlaylistListener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songList.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public void updateSongs(List<Song> newSongs) {
        songList.clear();
        if (newSongs != null) songList.addAll(newSongs);
        notifyDataSetChanged();
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvArtist;
        TextView tvDuration;
        ImageButton btnFavorite;
        ImageButton btnAddToPlaylist;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSongTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            btnAddToPlaylist = itemView.findViewById(R.id.btnAddToPlaylist);
        }

        public void bind(Song song, int position) {
            tvTitle.setText(song.getTitle());
            tvArtist.setText(song.getArtist());
            tvDuration.setText(formatTime((int) song.getDuration()));

            Log.d(TAG, "Binding song: " + song.getTitle() + ", repo: " + (repo != null ? "exists" : "NULL"));

            updateFavoriteIcon(song);

            // Song click
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onSongClick(position);
            });

            // Ensure buttons don’t trigger parent click
            btnFavorite.setClickable(true);
            btnFavorite.setFocusable(true);
            btnAddToPlaylist.setClickable(true);
            btnAddToPlaylist.setFocusable(true);

            // Favorite click
            btnFavorite.setOnClickListener(v -> {
                if (repo == null) {
                    Log.e(TAG, "LibraryRepository is NULL! Cannot toggle favorite.");
                    return;
                }
                boolean nowFav = repo.toggleFavorite(song);
                Log.d(TAG, "Toggled favorite to: " + nowFav);
                updateFavoriteIcon(song);
            });

            // Add to playlist
            btnAddToPlaylist.setOnClickListener(v -> {
                if (addToPlaylistListener != null) addToPlaylistListener.onAddToPlaylist(song);
            });
        }

        private void updateFavoriteIcon(Song song) {
            boolean isFav = repo != null && repo.isFavorite(song);
            btnFavorite.setImageResource(isFav
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
        }

        private String formatTime(int milliseconds) {
            int seconds = (milliseconds / 1000) % 60;
            int minutes = (milliseconds / (1000 * 60)) % 60;
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        }
    }
}
