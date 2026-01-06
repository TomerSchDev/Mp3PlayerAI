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
import com.tomersch.mp3playerai.utils.FavoritesManager;

import java.util.List;
import java.util.Locale;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    private static final String TAG = "SongAdapter";
    private List<Song> songList;
    private OnSongClickListener listener;
    private FavoritesManager favoritesManager;

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    public SongAdapter(List<Song> songList, OnSongClickListener listener) {
        this.songList = songList;
        this.listener = listener;
    }

    public void setFavoritesManager(FavoritesManager favoritesManager) {
        this.favoritesManager = favoritesManager;
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

    class SongViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvArtist;
        TextView tvDuration;
        ImageButton btnFavorite;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSongTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
        }

        public void bind(Song song, int position) {
            tvTitle.setText(song.getTitle());
            tvArtist.setText(song.getArtist());
            tvDuration.setText(formatTime((int) song.getDuration()));

            Log.d(TAG, "Binding song: " + song.getTitle() + ", favoritesManager: " + (favoritesManager != null ? "exists" : "NULL"));

            // Update heart icon based on favorite status
            updateFavoriteIcon(song);

            // Click on song (but not on favorite button)
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(position);
                }
            });

            // Make sure button doesn't trigger parent click
            btnFavorite.setClickable(true);
            btnFavorite.setFocusable(true);

            // Click on favorite button - remove any previous listeners first
            btnFavorite.setOnClickListener(v -> {
                Log.d(TAG, "Favorite button clicked for: " + song.getTitle());

                if (favoritesManager == null) {
                    Log.e(TAG, "FavoritesManager is NULL! Cannot toggle favorite.");
                    return;
                }
                boolean isFavorite = favoritesManager.toggleFavorite(song.getPath());
                Log.d(TAG, "Toggled favorite to: " + isFavorite);
                updateFavoriteIcon(song);
            });
        }

        private void updateFavoriteIcon(Song song) {
            if (favoritesManager != null && favoritesManager.isFavorite(song.getPath())) {
                // Filled heart for favorites
                btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
            } else {
                // Empty heart for non-favorites
                btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
            }
        }

        private String formatTime(int milliseconds) {
            int seconds = (milliseconds / 1000) % 60;
            int minutes = (milliseconds / (1000 * 60)) % 60;
            return String.format(Locale.ROOT,"%02d:%02d", minutes, seconds);
        }
    }
}