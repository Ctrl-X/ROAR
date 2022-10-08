package com.example.hummerclient.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.hummerclient.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link StartMenuFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StartMenuFragment extends Fragment {


    MenuTabCollectionAdapter menuTabCollectionAdapter;
    ViewPager2 viewPager;

    public StartMenuFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment StartMenuFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static StartMenuFragment newInstance() {
        StartMenuFragment fragment = new StartMenuFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_start_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        menuTabCollectionAdapter = new MenuTabCollectionAdapter(this);
        viewPager = view.findViewById(R.id.pager);
        viewPager.setAdapter(menuTabCollectionAdapter);

        //Retrieve the user pref to select the right tab if needed
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        Boolean isRemoteController = sharedPref.getBoolean(getString(R.string.pref_isRemoteController), false);


        final TabLayout.Tab[] currentTab = {null};

        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        // Rover tab
                        tab.setText("ROVER");
                    } else {
                        tab.setText("MANETTE");
                        if (isRemoteController) {
                            currentTab[0] = tab;
                        }
                    }
                }
        ).attach();
        if (currentTab[0] != null) {
            // The remote controller has been selected
            currentTab[0].select();
        }
    }
}

