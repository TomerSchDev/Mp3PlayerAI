package com.tomersch.mp3playerai.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.tomersch.mp3playerai.fragments.AllSongsFragment;
import com.tomersch.mp3playerai.fragments.CustomFragment;
import com.tomersch.mp3playerai.fragments.FavoritesFragment;
import com.tomersch.mp3playerai.fragments.FoldersFragment;
import com.tomersch.mp3playerai.fragments.PlaylistsFragment;

/**
 * Adapter for ViewPager2 to handle tab fragments
 */
public class MusicPagerAdapter extends FragmentStateAdapter {

    private AllSongsFragment allSongsFragment;
    private FavoritesFragment favoritesFragment;

    public MusicPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        allSongsFragment = AllSongsFragment.newInstance();
        favoritesFragment = FavoritesFragment.newInstance();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return favoritesFragment;
            case 1:
                return PlaylistsFragment.newInstance();
            case 3:
                return FoldersFragment.newInstance();
            case 4:
                return CustomFragment.newInstance();
            case 2:
            default:
                return allSongsFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 5; // Favorites, Playlists, All, Folders, Custom
    }

    public AllSongsFragment getAllSongsFragment() {
        return allSongsFragment;
    }

    public FavoritesFragment getFavoritesFragment() {
        return favoritesFragment;
    }
}