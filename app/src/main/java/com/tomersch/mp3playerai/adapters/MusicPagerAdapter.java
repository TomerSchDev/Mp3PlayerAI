package com.tomersch.mp3playerai.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.tomersch.mp3playerai.models.Tab;

public class MusicPagerAdapter extends FragmentStateAdapter {

    public MusicPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position < 0 || position >= Tab.tabs.length) {
            throw new IllegalArgumentException("Invalid position: " + position);
        }
        return Tab.tabs[position].create();
    }

    @Override
    public int getItemCount() {
        return Tab.tabs.length;
    }
}
