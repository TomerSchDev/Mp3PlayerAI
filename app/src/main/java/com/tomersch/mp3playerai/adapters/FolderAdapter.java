package com.tomersch.mp3playerai.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.models.MusicFolder;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {
    private List<MusicFolder> folderList;
    private OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(MusicFolder folder);
    }

    public FolderAdapter(List<MusicFolder> folderList, OnFolderClickListener listener) {
        this.folderList = folderList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MusicFolder folder = folderList.get(position);
        holder.tvFolderName.setText(folder.getFolderName());
        holder.tvCount.setText(folder.getSongCount() + " Songs");
        if (listener != null) {
            holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder));
        } else {
            Log.e("FolderAdapter", "Listener is null at position " + position);
        }
    }

    @Override
    public int getItemCount() { return folderList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFolderName, tvCount;
        ViewHolder(View v) {
            super(v);
            tvFolderName = v.findViewById(R.id.tvFolderName);
            tvCount = v.findViewById(R.id.tvSongCount);
        }
    }
}