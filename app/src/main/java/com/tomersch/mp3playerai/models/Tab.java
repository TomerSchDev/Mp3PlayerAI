package com.tomersch.mp3playerai.models;

import androidx.fragment.app.Fragment;

import com.tomersch.mp3playerai.fragments.AllSongsFragment;
import com.tomersch.mp3playerai.fragments.CustomFragment;
import com.tomersch.mp3playerai.fragments.FavoritesFragment;
import com.tomersch.mp3playerai.fragments.FoldersFragment;
import com.tomersch.mp3playerai.fragments.PlaylistsFragment;

public enum Tab {
    FAVORITES("Favorites") {
        @Override public Fragment create() { return FavoritesFragment.newInstance(); }
    },
    PLAYLISTS("Playlists") {
        @Override public Fragment create() { return PlaylistsFragment.newInstance(); }
    },
    ALL("All") {
        @Override public Fragment create() { return AllSongsFragment.newInstance(); }
    },
    FOLDERS("Folders") {
        @Override public Fragment create() { return FoldersFragment.newInstance(); }
    },
    CUSTOM("Custom") {
        @Override public Fragment create() { return CustomFragment.newInstance(); }
    };

    private final String label;

    Tab(String label) { this.label = label; }

    public String getLabel() { return label; }
    public abstract Fragment create();

    public static final Tab[] tabs = values();

    public static final String[] labels = {
            FAVORITES.label,
            PLAYLISTS.label,
            ALL.label,
            FOLDERS.label,
            CUSTOM.label
    };

    public Fragment getFragment() {
        return create();
    }
}
