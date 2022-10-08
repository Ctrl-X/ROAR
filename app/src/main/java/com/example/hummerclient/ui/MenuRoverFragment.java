package com.example.hummerclient.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.hummerclient.R;
import com.example.hummerclient.databinding.FragmentMenuRoverBinding;
import com.example.hummerclient.game.GameModel;
import com.google.android.material.textfield.TextInputEditText;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MenuRoverFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MenuRoverFragment extends Fragment {

    private GameModel gameModel;
    private FragmentMenuRoverBinding binding;
    private TextInputEditText inputServerAddr;

    public MenuRoverFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MenuRoverFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MenuRoverFragment newInstance() {
        MenuRoverFragment fragment = new MenuRoverFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
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
        binding = FragmentMenuRoverBinding.inflate(inflater, container, false);
        View root = binding.getRoot();


        inputServerAddr = binding.inputServerAddr;
        inputServerAddr.setOnFocusChangeListener((View view, boolean hasFocus) -> {
            if (!hasFocus) {
                String receiveAddr = inputServerAddr.getText().toString();
                gameModel.setReceiverAddr(receiveAddr);
            }
        });
        gameModel.getReceiverAddr().observe(getViewLifecycleOwner(), inputServerAddr::setText);

        //Retrieve the user pref to select the right tab if needed
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        String serverAddr = sharedPref.getString(getString(R.string.pref_serverAddr), "");
        gameModel.setReceiverAddr(serverAddr);


        // Action button
        final Button actionButton = binding.btnAction;
        actionButton.setOnClickListener(e -> {
            inputServerAddr.clearFocus();

            // This is the remote controller, so for next launch, act as is.
            SharedPreferences.Editor editor = sharedPref.edit();
            String destAddress = inputServerAddr.getText().toString();
            editor.putString(getString(R.string.pref_serverAddr),destAddress );
            editor.putBoolean(getString(R.string.pref_isRemoteController), false);
            editor.apply();

            gameModel.setIsRemoteController(false);
            gameModel.setIsRunning(true);
        });



        // Inflate the layout for this fragment
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();


//        TextView textView = ((TextView) view.findViewById(R.id.textObject1));
//
//        textView.setText(Integer.toString(args.getInt(ARG_OBJECT)));
    }
}