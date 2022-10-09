package com.example.hummerclient.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.example.hummerclient.R;
import com.example.hummerclient.databinding.FragmentMenuManetteBinding;
import com.example.hummerclient.networking.NetworkUtils;
import com.example.hummerclient.networking.UDP_PORT;
import com.example.hummerclient.game.GameModel;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MenuManetteFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MenuManetteFragment extends Fragment {
    public static final String ARG_OBJECT = "object";


    private GameModel gameModel;
    private FragmentMenuManetteBinding binding;
    private TextView txt_ipAddressView;


    public MenuManetteFragment() {
        // Required empty public constructor
    }


    public static MenuManetteFragment newInstance() {
        MenuManetteFragment fragment = new MenuManetteFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        gameModel = new ViewModelProvider(requireActivity()).get(GameModel.class);

        binding = FragmentMenuManetteBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        final TextView ipToolTip = binding.textViewIpTooltip;
        txt_ipAddressView = binding.textViewIPAddress;

        gameModel.getMyAddr().observe(getViewLifecycleOwner(), myAddr -> {
            txt_ipAddressView.setText("Addresse WAN à indiquer pour le ROVER : " + myAddr);
            ipToolTip.setText("Si vous etes derriere un routeur (ex : à la maison), ajoutez sur le routeur une redirection du port UDP " + UDP_PORT.REMOTE_CONTROLLER + " vers l'addresse ip " + NetworkUtils.getIPAddress(true));
        });

        // Action button
        final Button actionButton = binding.btnManetteAction;
        actionButton.setOnClickListener(e -> {

            // This is the remote controller, so for next launch, act as is.
            SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(getString(R.string.pref_isRemoteController), true);
            editor.apply();

            gameModel.setIsRemoteController(true);
            gameModel.setIsRunning(true);
        });

        // Inflate the layout for this fragment
        return root;
    }

}