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

import java.util.HashMap;
import java.util.Map;


/**
 * Adapter for ViewPager2 to handle tab fragments
 */
public class MusicPagerAdapter extends FragmentStateAdapter {
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position >= 0 && position < this.tabsArray.length) {
            return tabs.get(this.tabsArray[position]);
        }
        return tabs.get(Tab.ALL);
    }

    @Override
    public int getItemCount() {
        return 5; // Favorites, Playlists, All, Folders, Custom
    }


    public static enum Tab {
        FAVORITES, PLAYLISTS, ALL, FOLDERS, CUSTOM
    }

    private final Map<Tab,Fragment> tabs= new HashMap<>();
    private final Tab[] tabsArray = Tab.values();

    public MusicPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        tabs.put(Tab.FAVORITES, FavoritesFragment.newInstance());
        tabs.put(Tab.PLAYLISTS, PlaylistsFragment.newInstance());
        tabs.put(Tab.ALL, AllSongsFragment.newInstance());
        tabs.put(Tab.FOLDERS, FoldersFragment.newInstance());
        tabs.put(Tab.CUSTOM, CustomFragment.newInstance());

    }
    public Fragment getTab(Tab tab)
    {
        return tabs.get(tab);
    }


}

