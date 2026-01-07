package com.tomersch.mp3playerai.fragments;

import android.content.Context;
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
import com.tomersch.mp3playerai.adapters.SongAdapter;
import com.tomersch.mp3playerai.models.Song;
import com.tomersch.mp3playerai.player.LibraryProvider;
import com.tomersch.mp3playerai.player.PlayerController;
import com.tomersch.mp3playerai.utils.LibraryRepository;
import com.tomersch.mp3playerai.utils.PlaylistDialogHelper;

import java.util.ArrayList;

/**
 * Reusable container fragment for displaying a song list with a header.
 * Used for folders and playlists.
 */
public class ListContainerFragment extends Fragment {

    private static final String ARG_SONGS = "songs";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUBTITLE = "subtitle";
    private static final String ARG_PLAYLIST_ID = "playlist_id";

    private ArrayList<Song> songs = new ArrayList<>();
    private String title;
    private String subtitle;
    private String playlistId; // null if folder context

    private PlayerController playerController;
    private LibraryRepository repo;

    public ListContainerFragment() {}

    public static ListContainerFragment newInstance(ArrayList<Song> songs, String title, String subtitle) {
        ListContainerFragment fragment = new ListContainerFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SONGS, songs);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        fragment.setArguments(args);
        return fragment;
    }

    public static ListContainerFragment newInstanceForPlaylist(
            ArrayList<Song> songs,
            String title,
            String subtitle,
            String playlistId
    ) {
        ListContainerFragment fragment = new ListContainerFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SONGS, songs);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        args.putString(ARG_PLAYLIST_ID, playlistId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof PlayerController) {
            playerController = (PlayerController) context;
        } else {
            throw new IllegalStateException("Host activity must implement PlayerController");
        }

        if (context instanceof LibraryProvider) {
            repo = ((LibraryProvider) context).getLibraryRepository();
        } else {
            throw new IllegalStateException("Host activity must implement LibraryProvider");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            ArrayList<Song> passed = (ArrayList<Song>) args.getSerializable(ARG_SONGS);
            if (passed != null) songs = passed;

            title = args.getString(ARG_TITLE);
            subtitle = args.getString(ARG_SUBTITLE);
            playlistId = args.getString(ARG_PLAYLIST_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_list_container, container, false);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSubtitle);
        FrameLayout contentContainer = view.findViewById(R.id.contentContainer);

        if (tvTitle != null && title != null) tvTitle.setText(title);
        if (tvSubtitle != null && subtitle != null) tvSubtitle.setText(subtitle);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                }
            });
        }

        if (contentContainer != null) {
            RecyclerView recyclerView = new RecyclerView(requireContext());
            recyclerView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

            // Padding for mini-player
            recyclerView.setClipToPadding(false);
            recyclerView.setPadding(0, 0, 0, (int) (80 * getResources().getDisplayMetrics().density));

            SongAdapter adapter = buildAdapter();
            recyclerView.setAdapter(adapter);

            contentContainer.removeAllViews();
            contentContainer.addView(recyclerView);
        }

        return view;
    }

    @NonNull
    private SongAdapter buildAdapter() {
        // Play current "songs" list
        SongAdapter.OnSongClickListener clickListener = position -> {
            if (playerController != null) {
                playerController.playQueue(songs, position);
            }
        };

        // Add/remove to playlists using repo (context-aware)
        SongAdapter.OnAddToPlaylistListener addToPlaylistListener = song -> {
            if (repo == null) return;

            if (playlistId != null) {
                PlaylistDialogHelper.showAddToPlaylistDialogInContext(
                        getContext(),
                        song,
                        repo,
                        playlistId
                );
            } else {
                PlaylistDialogHelper.showAddToPlaylistDialog(
                        getContext(),
                        song,
                        repo
                );
            }
        };

        SongAdapter adapter = new SongAdapter(songs, clickListener);
        adapter.setOnAddToPlaylistListener(addToPlaylistListener);

        // IMPORTANT: your SongAdapter currently still uses FavoritesManager.
        // Since you removed it, you must update SongAdapter to use LibraryRepository instead.
        // See my "SongAdapter fix" section below.
        adapter.setRepository(repo);

        return adapter;
    }
}
