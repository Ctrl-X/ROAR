package com.example.hummerclient.game;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.hummerclient.databinding.FragmentSpeedUIBinding;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class SpeedUIFragment extends Fragment {

    private GameModel gameModel;
    private FragmentSpeedUIBinding binding;

    public SpeedUIFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentSpeedUIBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // disable the touche event on the speed bar
        root.setOnTouchListener((v, event) -> true);



        gameModel = new ViewModelProvider(requireActivity()).get(GameModel.class);
        gameModel.getSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed > 127) {
                speed -= 128;
                binding.viewBrake.setBackgroundColor(Color.RED);
            } else {
                binding.viewBrake.setBackgroundColor(Color.GREEN);
            }
            binding.seekBarSpeed.setProgress(speed);
        });
        binding.seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gameModel.setSpeed(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        gameModel.getDirection().observe(getViewLifecycleOwner(), binding.seekBarDirection::setProgress);
        binding.seekBarDirection.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gameModel.setDirection(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        return root;
    }
}