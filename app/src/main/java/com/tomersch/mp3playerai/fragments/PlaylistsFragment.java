package com.tomersch.mp3playerai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tomersch.mp3playerai.R;

/**
 * Fragment for Playlists tab (placeholder for future implementation)
 */
public class PlaylistsFragment extends Fragment {

    public static PlaylistsFragment newInstance() {
        return new PlaylistsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.placeholder, container, false);
        TextView tvTitle = view.findViewById(R.id.tvPlaceholder);
        tvTitle.setText("Playlists\n\nComing Soon!");
        return view;
    }
}