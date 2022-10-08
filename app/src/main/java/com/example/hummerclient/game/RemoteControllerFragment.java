package com.example.hummerclient.game;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.example.hummerclient.databinding.FragmentRemoteControllerBinding;
import com.example.hummerclient.networking.DataSender;
import com.example.hummerclient.networking.DataSenderRemoteController;
import com.example.hummerclient.networking.UdpTransmitter;
import com.example.hummerclient.ui.UIRunnerInterface;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class RemoteControllerFragment extends BaseFragment {


    private FragmentRemoteControllerBinding binding;

    public RemoteControllerFragment() {
    //For remote controller :
    // ingress by remote controller port
    // egress : send on the ROVER port
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        binding = FragmentRemoteControllerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();


        return root;
    }

    @Override
    protected void startUdpTransmission() {
        super.startUdpTransmission();

        gameModel.setStatus("En attente du Rover...");
        Log.i(TAG, "Remote Controller is now Running...");

    }


    @Override
    UdpTransmitter buildDataReceiver() {
        return buildDataReceiver(UdpTransmitter.REMOTE_CONTROLLER_PORT);
    }
    @Override
    protected DataSender buildDataSender() {
        UIRunnerInterface callback = () -> getActivity().runOnUiThread(sendedMessage);

        DataSenderRemoteController dataSyncRunner = new DataSenderRemoteController(callback);

        gameModel.getSpeed().observe(this, dataSyncRunner::setSpeed);
        gameModel.getDirection().observe(this, dataSyncRunner::setDirection);

        return dataSyncRunner;
    }




    /**
     * Response received from the rover
     *
     * @param dataSet contains [key,values...] data like IP address
     */
    @Override
    protected void processReceivedData(String[] dataSet) {
        if (dataSet.length < 2) {
            return;
        }
        String action = dataSet[0];
        switch (action) {
            case "IP":
                // We received the IP addresse of the rover, update it
                String value = dataSet[1];
                gameModel.setReceiverAddr("192.168.0.194");
//                homeViewModel.setReceiverAddr(value);
                Log.i(TAG, " updated Rover IP to " + value);
                break;
        }
    }


}