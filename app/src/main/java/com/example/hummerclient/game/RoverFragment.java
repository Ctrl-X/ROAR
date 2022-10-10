package com.example.hummerclient.game;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.hummerclient.arduino.Arduino;
import com.example.hummerclient.arduino.ArduinoListener;
import com.example.hummerclient.databinding.FragmentRoverBinding;
import com.example.hummerclient.networking.DataSender;
import com.example.hummerclient.networking.DataSenderRover;
import com.example.hummerclient.networking.UDP_PORT;
import com.example.hummerclient.networking.UdpTransmitter;
import com.example.hummerclient.ui.UIRunnerInterface;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.rtplibrary.rtsp.RtspCamera2;
import com.pedro.rtplibrary.view.OpenGlView;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

import java.nio.ByteBuffer;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class RoverFragment extends BaseFragment implements ArduinoListener, ConnectCheckerRtsp, SurfaceHolder.Callback, View.OnLongClickListener {

    private Arduino arduino;
    private FragmentRoverBinding binding;
    private RtspCamera2 rtspCamera2;
    private OpenGlView openGlView;
    private boolean isSurfaceReady = false;
    private int displayRotation;


    public RoverFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();


    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentRoverBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        openGlView = binding.surfaceView;
        displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();

        gameModel.getReceiverAddr().observe(getViewLifecycleOwner(), receiverAddr -> startStreaming());

        //create builder
        // Streaming Lib from : https://github.com/pedroSG94/rtmp-rtsp-stream-client-java
        rtspCamera2 = new RtspCamera2(openGlView, this);
        rtspCamera2.getGlInterface().setFilter(new NoFilterRender());
        rtspCamera2.setProtocol(Protocol.UDP);

        openGlView.getHolder().addCallback(this);
        openGlView.setOnClickListener(v -> container.callOnClick());
        openGlView.setOnLongClickListener(this);
        // Inflate the layout for this fragment
        return root;
    }


    @Override
    protected void startUdpTransmission() {
        super.startUdpTransmission();

        gameModel.setStatus("En attente de la manette...");
        arduino = new Arduino(this.getContext());
        arduino.setArduinoListener(this);
        Log.i(TAG, "Rover is now Running...");

        //start stream
        startStreaming();
    }


    @Override
    protected void stopUdpTransmission() {
        super.stopUdpTransmission();
        if (arduino != null) {
            arduino.unsetArduinoListener();
            arduino.close();
            arduino = null;
        }
        stopStreaming();

    }

    @Override
    UdpTransmitter buildDataReceiver() {
        return buildDataReceiver(UDP_PORT.ROVER);
    }

    @Override
    protected DataSender buildDataSender() {
        UIRunnerInterface callback = () -> getActivity().runOnUiThread(sendedMessage);

        DataSenderRover dataSyncRunner = new DataSenderRover(callback);

        return dataSyncRunner;
    }

    @Override
    protected void processReceivedData(String[] dataSet) {
        if (dataSet.length < 2) {
            return;
        }
        String action = dataSet[0];
        switch (action) {
            case "MV":
                //Move action
                String speed = dataSet[1];
                String direction = dataSet[2];
                int intSpeed = Integer.parseInt(speed);
                int intDirection = Integer.parseInt(direction);
                changeSpeed(intSpeed);
                changeDirection(intDirection);
                ByteBuffer b = ByteBuffer.allocate(3);

                //b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a byte buffer is always BIG_ENDIAN.
                b.put((byte) intSpeed);
                b.put((byte) intDirection);
                b.put((byte) '\n');
                byte[] dataToSendToArduino = b.array();
                Log.i(TAG, " data to send to arduino " + dataToSendToArduino.toString());

                if (arduino != null && arduino.isOpened()) {
                    arduino.send(dataToSendToArduino);
                }
                break;
        }
    }

    private void startStreaming() {
        String receiverAddr = gameModel.getReceiverAddr().getValue();
        int port = UDP_PORT.SERVER_RTSP_RCV_PORT.getValue();
        if (isSurfaceReady && !TextUtils.isEmpty(receiverAddr)) {
            if (!rtspCamera2.isStreaming()) {
                if (rtspCamera2.isRecording()
                        || rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
                    String endpoint = "rtsp://" + receiverAddr + ":" + port + "/live/rover";
                    Log.i(TAG,"Start Streaming to " + endpoint);
                    rtspCamera2.startStream(endpoint);
                } else {
                    /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
                    Toast.makeText(requireActivity(), "Error preparing stream, This device cant do it",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void stopStreaming() {
        if (rtspCamera2.isRecording()) {
            rtspCamera2.stopRecord();
        }
        if (rtspCamera2.isStreaming()) {
            rtspCamera2.stopStream();
        }
        rtspCamera2.stopPreview();
    }


    //################ ARDUINO INTERFACE IMPLEMENTATION ###############
    @Override
    public void onArduinoAttached(UsbDevice device) {
        Log.i(TAG, "arduino attached : " + device.getDeviceName());
        arduino.open(device);
    }

    @Override
    public void onArduinoDetached() {
        Log.i(TAG, "arduino detached");

    }

    @Override
    public void onArduinoMessage(byte[] bytes) {
        Log.i(TAG, "Got data from Arduino : " + new String(bytes));
    }

    @Override
    public void onArduinoOpened() {
        Log.i(TAG, "onArduinoOpened ");
//        String str = "arduino opened...";
//        arduino.send(str.getBytes());
    }

    @Override
    public void onUsbPermissionDenied() {
        Log.i(TAG, "Permission denied. Attempting again in 3 sec...");
//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                if (arduino != null) {
////                    arduino.reopen();
//                }
//            }
//        }, 3000);
    }


    @Override
    public void onAuthErrorRtsp() {
        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Auth error", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAuthSuccessRtsp() {
        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Auth success", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onConnectionFailedRtsp(@NonNull String reason) {
        getActivity().runOnUiThread(() -> {
            Log.i(TAG, "RTSP Connection failed : " + reason);
            Toast.makeText(requireActivity(), "Connection failed. " + reason, Toast.LENGTH_SHORT)
                    .show();
            rtspCamera2.reConnect(5000,null);
        });
    }

    @Override
    public void onConnectionStartedRtsp(@NonNull String s) {

    }

    @Override
    public void onConnectionSuccessRtsp() {
        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Connection success", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDisconnectRtsp() {
        getActivity().runOnUiThread(() -> {
            Log.i(TAG, "RTSP Disconnected");
            Toast.makeText(requireActivity(), "Disconnected", Toast.LENGTH_SHORT).show();
            rtspCamera2.reConnect(5000,null);
        });
    }

    @Override
    public void onNewBitrateRtsp(long l) {

    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        isSurfaceReady = true;
        startStreaming();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

        int currentRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();

        if (displayRotation != currentRotation) {
            displayRotation = currentRotation;
            rtspCamera2.stopPreview();
            rtspCamera2.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopStreaming();
    }

    @Override
    public boolean onLongClick(View v) {
        try {
            rtspCamera2.switchCamera();
        } catch (CameraOpenException e) {
            Toast.makeText(requireActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return true;
    }

}