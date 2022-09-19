package com.example.hummerclient.ui.home;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class DemoCollectionAdapter extends FragmentStateAdapter {
    public DemoCollectionAdapter(Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Return a NEW fragment instance in createFragment(int)
        Bundle args = new Bundle();
        Fragment fragment;
        if (position == 0) {
            fragment = new MenuRoverFragment();
        } else {
            fragment = new MenuManetteFragment();
            // Exemple of passing args :
//            args.putInt(MenuManetteFragment.ARG_OBJECT, position + 1);
//            fragment.setArguments(args);
        }

        return fragment;
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
