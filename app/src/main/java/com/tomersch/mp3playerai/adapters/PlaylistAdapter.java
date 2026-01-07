package com.tomersch.mp3playerai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.models.Playlist;

import java.util.List;

/**
 * Adapter for displaying playlists
 */
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
    
    private List<Playlist> playlists;
    private OnPlaylistClickListener clickListener;
    private OnPlaylistLongClickListener longClickListener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
    }

    public interface OnPlaylistLongClickListener {
        void onPlaylistLongClick(Playlist playlist);
    }

    public PlaylistAdapter(List<Playlist> playlists, OnPlaylistClickListener clickListener) {
        this.playlists = playlists;
        this.clickListener = clickListener;
    }

    public void setOnPlaylistLongClickListener(OnPlaylistLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlaylistName;
        TextView tvSongCount;
        ImageButton btnOptions;

        ViewHolder(View itemView) {
            super(itemView);
            tvPlaylistName = itemView.findViewById(R.id.tvPlaylistName);
            tvSongCount = itemView.findViewById(R.id.tvSongCount);
            btnOptions = itemView.findViewById(R.id.btnOptions);
        }

        void bind(Playlist playlist) {
            tvPlaylistName.setText(playlist.getName());
            
            int count = playlist.getSongCount();
            String countText = count + " song" + (count != 1 ? "s" : "");
            tvSongCount.setText(countText);

            // Click to open playlist
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onPlaylistClick(playlist);
                }
            });

            // Long click for options
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onPlaylistLongClick(playlist);
                    return true;
                }
                return false;
            });

            // Options button
            btnOptions.setOnClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onPlaylistLongClick(playlist);
                }
            });
        }
    }
}
