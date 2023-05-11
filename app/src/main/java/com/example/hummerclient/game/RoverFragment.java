package com.example.hummerclient.game;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.rtplibrary.util.FpsListener;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import com.pedro.rtspserver.RtspServerCamera1;
import com.pedro.rtspserver.RtspServerCamera2;

import java.nio.ByteBuffer;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class RoverFragment extends BaseFragment implements ArduinoListener, ConnectCheckerRtsp, SurfaceHolder.Callback, View.OnLongClickListener, FpsListener.Callback {

    private Arduino arduino;
    private FragmentRoverBinding binding;
    private RtspServerCamera1 rtspCamera;
    private OpenGlView openGlView;
    private boolean isSurfaceReady = false;
    private int displayRotation;
    private TextView txtFps;
    private TextView txtBitrate;
    private TextView txtStreamRes;


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

        txtFps = binding.txtFps;
        txtBitrate = binding.txtBitrate;
        txtStreamRes = binding.txtStreamRes;

        openGlView = binding.surfaceView;
        displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();

        gameModel.getReceiverAddr().observe(getViewLifecycleOwner(), receiverAddr -> startStreaming());

        //create builder
        // Streaming Lib from : https://github.com/pedroSG94/RTSP-Server
        // Can test Stream address in VLC with a network stream like rtsp://100.106.84.231:19000/

        // RtspServerCamera1
        rtspCamera = new RtspServerCamera1(openGlView, this, UDP_PORT.SERVER_RTSP_RCV_PORT.getValue());
//        rtspCamera.setFpsListener(this);
        rtspCamera.switchCamera(0); // select the back facing camera by default


        // RtspServerCamera2
//        rtspCamera = new RtspServerCamera2(openGlView, this, UDP_PORT.SERVER_RTSP_RCV_PORT.getValue());
//        rtspCamera.setFpsListener(this);
//        rtspCamera.getGlInterface().setFilter(new NoFilterRender());


        // No preview
//        rtspCamera = new RtspServerCamera2(this.getContext(),true, this, UDP_PORT.SERVER_RTSP_RCV_PORT.getValue());
//        rtspCamera.setFpsListener(this);

//        rtspCamera1.setLimitFPSOnFly(10);
//        rtspCamera.setReTries(20);
//        rtspCamera2.setVideoCodec(VideoCodec.H264);
//        rtspCamera2.disableAudio();


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
        if (isSurfaceReady && !TextUtils.isEmpty(receiverAddr)) {
            if (!rtspCamera.isStreaming()) {
                // video
                int width = 640;  // default : 640
                int height = 480;  // default : 480
                int fps = 30;  // default : 30
                int iFrameInterval = 2; // default : 2 (key frame interval  in seconds)
                int rotation = CameraHelper.getCameraOrientation(this.getContext());
                int bitrate = 1200 * 1024;  // default : 1200 * 1024 aka 1.2Mo / sec

                // audio
                int audio_bitrate = 8*1024; //feault : 64*1024
                int sampleRate= 8000; // default 32000
                boolean isStereo = false;
                boolean echoCanceler  = true;
                boolean noiseSuppressor = false;


                if (rtspCamera.isRecording() ||
//                        (rtspCamera1.prepareAudio(audio_bitrate,sampleRate,isStereo,echoCanceler,noiseSuppressor) &&
                        rtspCamera.prepareVideo(width, height, fps, bitrate, iFrameInterval, rotation)) {
//                        || rtspCamera2.prepareVideo()) {
                    Log.i(TAG, "Start Streaming : " + rtspCamera.getEndPointConnection());
//                    rtspCamera1.startPreview(0,640,480,15,90);
                    rtspCamera.startStream();

//                    rtspCamera1.setVideoBitrateOnFly(122880);


//                    rtspCamera1.setPreviewOrientation(0);
//                    txtStreamRes.setText("res : " + rtspCamera1.getResolutionValue());
                    txtStreamRes.setText(rtspCamera.getStreamWidth() + " x " + rtspCamera.getStreamHeight());

                } else {
                    /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
                    Toast.makeText(requireActivity(), "Error preparing stream, This device cant do it",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void stopStreaming() {
        if (rtspCamera.isRecording()) {
            rtspCamera.stopRecord();
        }
        if (rtspCamera.isStreaming()) {
            rtspCamera.stopStream();
        }
        rtspCamera.stopPreview();
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
        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Auth error", Toast.LENGTH_LONG).show());
    }

    @Override
    public void onAuthSuccessRtsp() {
        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Auth success", Toast.LENGTH_LONG).show());
    }

    @Override
    public void onConnectionFailedRtsp(@NonNull String reason) {
        getActivity().runOnUiThread(() -> {
            Log.i(TAG, "RTSP Connection failed : " + reason);
            Toast.makeText(requireActivity(), "Connection failed. " + reason, Toast.LENGTH_LONG)
                    .show();
            rtspCamera.stopStream();
        });
    }

    @Override
    public void onConnectionStartedRtsp(@NonNull String s) {
        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Connection STARTED", Toast.LENGTH_LONG).show());
    }

    @Override
    public void onConnectionSuccessRtsp() {
        getActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Connection success", Toast.LENGTH_LONG).show());
        rtspCamera.resetSentVideoFrames();
        //        rtspCamera1.resetDroppedVideoFrames();
    }

    @Override
    public void onDisconnectRtsp() {
        getActivity().runOnUiThread(() -> {
            Log.i(TAG, "RTSP Disconnected");
            Toast.makeText(requireActivity(), "Disconnected", Toast.LENGTH_LONG).show();
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
            if (displayRotation == 1) {
                rtspCamera.setPreviewOrientation(0);
            } else {
                rtspCamera.setPreviewOrientation(90);
            }
            txtStreamRes.setText(rtspCamera.getStreamWidth() + " x " + rtspCamera.getStreamHeight());

//            rtspCamera2.stopPreview();
//            rtspCamera2.startPreview();
//
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopStreaming();
    }

    @Override
    public boolean onLongClick(View v) {
        try {
            rtspCamera.switchCamera();
        } catch (CameraOpenException e) {
            Toast.makeText(requireActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    public void onFps(int fps) {
        getActivity().runOnUiThread(() -> {
            int bitRate = rtspCamera.getBitrate();
            txtFps.setText(fps + " Fps");
            txtBitrate.setText("congestion : " + rtspCamera.hasCongestion());
//            txtBitrate.setText(bitRate / 1024 + " Kbits per sec");
            //rtspCamera1.resetDroppedVideoFrames();

        });

    }
}