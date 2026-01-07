package com.tomersch.mp3playerai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.activities.MainActivity;
import com.tomersch.mp3playerai.adapters.SongAdapter;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.utils.FavoritesManager;

import java.util.ArrayList;

/**
 * Reusable container fragment for displaying song lists with a header
 * Used for folders and playlists
 */
public class ListContainerFragment extends Fragment {

    private static final String ARG_SONGS = "songs";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUBTITLE = "subtitle";

    private ArrayList<Song> songs;
    private String title;
    private String subtitle;

    /**
     * Factory method to create new instance
     */
    public static ListContainerFragment newInstance(ArrayList<Song> songs, String title, String subtitle) {
        ListContainerFragment fragment = new ListContainerFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SONGS, songs);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        fragment.setArguments(args);
        return fragment;
    }
    private ListContainerFragment() {};

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            songs = (ArrayList<Song>) getArguments().getSerializable(ARG_SONGS);
            title = getArguments().getString(ARG_TITLE);
            subtitle = getArguments().getString(ARG_SUBTITLE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list_container, container, false);

        // Use the correct IDs from your layout
        ImageButton btnBack = view.findViewById(R.id.btnBack);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSubtitle);
        FrameLayout contentContainer = view.findViewById(R.id.contentContainer);

        // Set title and subtitle
        if (tvTitle != null && title != null) {
            tvTitle.setText(title);
        }
        if (tvSubtitle != null && subtitle != null) {
            tvSubtitle.setText(subtitle);
        }

        // Back button functionality
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                }
            });
        }

        // Create RecyclerView programmatically
        if (contentContainer != null && songs != null) {
            RecyclerView recyclerView = new RecyclerView(requireContext());
            recyclerView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

            // Add bottom padding for mini player
            recyclerView.setClipToPadding(false);
            recyclerView.setPadding(0, 0, 0, (int) (80 * getResources().getDisplayMetrics().density));

            // Set up adapter
            SongAdapter adapter = getSongAdapter();

            recyclerView.setAdapter(adapter);
            contentContainer.addView(recyclerView);
        }

        return view;
    }

    @NonNull
    private SongAdapter getSongAdapter() {
        SongAdapter.OnSongClickListener clickListener = ( position) -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).playSong(songs, position);
            }
        };

        SongAdapter adapter = new SongAdapter(songs, clickListener);

        // Get FavoritesManager from MainActivity
        if (getActivity() instanceof MainActivity) {
            FavoritesManager favoritesManager = ((MainActivity) getActivity()).getFavoritesManager();
            if (favoritesManager != null) {
                adapter.setFavoritesManager(favoritesManager);
            }
        }
        return adapter;
    }
}