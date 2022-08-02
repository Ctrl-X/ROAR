package com.example.hummerclient.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.hummerclient.networking.NetworkUtils;
import com.example.hummerclient.R;
import com.example.hummerclient.networking.UdpTransmitter;
import com.example.hummerclient.databinding.FragmentHomeBinding;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;

    private TextInputLayout layoutServerClient;
    private TextView txt_status;
    private TextView txt_ipAddressView;
    private LinearLayout layoutMyAddr;
    private TextInputEditText inputServerAddr;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        txt_status = binding.textStatus;
        homeViewModel.getStatus().observe(getViewLifecycleOwner(), txt_status::setText);

        homeViewModel.getSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed > 127) {
                speed -= 128;
                binding.viewBrake.setBackgroundColor(Color.RED);
            } else {
                binding.viewBrake.setBackgroundColor(Color.GREEN);
            }
            binding.seekBarSpeed.setProgress(speed);
        });
        homeViewModel.getDirection().observe(getViewLifecycleOwner(), binding.seekBarDirection::setProgress);
// TODO : faire pour que le binding.seekBarDirection modifie la direction si on la touche manuellement
// idem pour la speed

        inputServerAddr = binding.inputServerAddr;
        inputServerAddr.setOnFocusChangeListener((View view, boolean hasFocus) -> {
            if (!hasFocus) {
                String receiveAddr = inputServerAddr.getText().toString();
                homeViewModel.setReceiverAddr(receiveAddr);
            }
        });
        homeViewModel.getReceiverAddr().observe(getViewLifecycleOwner(), inputServerAddr::setText);


        final TextView ipToolTip = binding.textViewIpTooltip;
        txt_ipAddressView = binding.textViewIPAddress;
        homeViewModel.getMyAddr().observe(getViewLifecycleOwner(), myAddr -> {
            txt_ipAddressView.setText("Addresse à indiquer sur le client : " + myAddr);
            ipToolTip.setText("Si vous etes derriere un routeur, rediriger le port UDP " + UdpTransmitter.SERVER_PORT + " vers l'addresse ip : " + NetworkUtils.getIPAddress(true));
        });


        final Switch serverSwitch = binding.switchServer;
        layoutMyAddr = binding.layoutMyIpAddr;
        layoutServerClient = binding.layoutInputServer;
        serverSwitch.setOnCheckedChangeListener((CompoundButton compoundButton, boolean isServer) -> {
            homeViewModel.setIsServer(isServer);
            if (isServer) {
                layoutServerClient.setVisibility(View.GONE);
                layoutMyAddr.setVisibility(View.VISIBLE);
            } else {
                layoutServerClient.setVisibility(View.VISIBLE);
                layoutMyAddr.setVisibility(View.GONE);
            }
        });


        final Button actionButton = binding.btnAction;
        actionButton.setOnClickListener(e -> {
            inputServerAddr.clearFocus();
            boolean isRunning = Boolean.TRUE.equals(homeViewModel.getIsRunning().getValue());
            isRunning = !isRunning;
            homeViewModel.setIsRunning(isRunning);
            if (isRunning) {
                actionButton.setText(R.string.home_Stop);
                serverSwitch.setEnabled(false);
                inputServerAddr.setEnabled(false);
                SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(getString(R.string.pref_serverAddr), inputServerAddr.getText().toString());
                editor.apply();
            } else {
                actionButton.setText(R.string.home_Start);
                serverSwitch.setEnabled(true);
                inputServerAddr.setEnabled(true);
            }
        });

        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        String defaultAddr = getResources().getString(R.string.pref_serverAddr);
        String serverAddr = sharedPref.getString(getString(R.string.pref_serverAddr), defaultAddr);

        if (serverAddr != null) {
            homeViewModel.setReceiverAddr(serverAddr);
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        txt_ipAddressView = null;
        txt_status = null;

    }
}