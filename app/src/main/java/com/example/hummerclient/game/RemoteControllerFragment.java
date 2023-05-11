package com.example.hummerclient.game;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.fragment.app.Fragment;

import com.example.hummerclient.databinding.FragmentRemoteControllerBinding;
import com.example.hummerclient.networking.DataSender;
import com.example.hummerclient.networking.DataSenderRemoteController;
import com.example.hummerclient.networking.UDP_PORT;
import com.example.hummerclient.networking.UdpTransmitter;
import com.example.hummerclient.ui.UIRunnerInterface;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class RemoteControllerFragment extends BaseFragment {


    private FragmentRemoteControllerBinding binding;
    private String previousReceiveAddr = null;
    private VideoView videoView;

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
        videoView = binding.videoView;
//        MediaPlayer player = new MediaPlayer();
        MediaController mc = new MediaController(getContext());
        videoView.setMediaController(mc);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

            public void onPrepared(MediaPlayer mp) {
                videoView.start();
            }
        });


        gameModel.getReceiverAddr().observe(getViewLifecycleOwner(), receiverAddr -> {
                    if (!TextUtils.isEmpty(receiverAddr) && !Objects.equals(receiverAddr, previousReceiveAddr)) {
                        int port = UDP_PORT.SERVER_RTSP_RCV_PORT.getValue();
                        previousReceiveAddr = receiverAddr;
                        String endpoint = "rtsp://" + receiverAddr + ":" + port;
                        Log.i(TAG,"Receive Streaming from " + endpoint);
                        videoView.setVideoURI(Uri.parse(endpoint));
                        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Connecting to " + endpoint, Toast.LENGTH_LONG).show());

                    }
                }
        );


        return root;
    }

    @Override
    protected void startUdpTransmission() {
        super.startUdpTransmission();

        gameModel.setStatus("En attente du Rover...");
        Log.i(TAG, "Remote Controller is now Running...");


    }

    @Override
    protected void stopUdpTransmission() {
        super.stopUdpTransmission();

    }


    @Override
    UdpTransmitter buildDataReceiver() {
        return buildDataReceiver(UDP_PORT.REMOTE_CONTROLLER);
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
                //gameModel.setReceiverAddr(value);
//                homeViewModel.setReceiverAddr(value);
                Log.i(TAG, " updated Rover IP to " + value);
                break;
        }
    }

}