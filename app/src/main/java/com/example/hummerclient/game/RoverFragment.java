package com.example.hummerclient.game;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
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

import java.nio.ByteBuffer;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class RoverFragment extends BaseFragment implements ArduinoListener,  View.OnLongClickListener {

    private Arduino arduino;
    private FragmentRoverBinding binding;
    private TextView txtStatus;
    private int direction;
    private int speed;


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
        txtStatus = binding.txtStreamRes;

        gameModel.getReceiverAddr().observe(getViewLifecycleOwner(), receiverAddr -> startStreaming());

        gameModel.getSpeed().observe(getViewLifecycleOwner(), this::setSpeed);
        gameModel.getDirection().observe(getViewLifecycleOwner(), this::setDirection);


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

    private void refreshArduino(){
        ByteBuffer b = ByteBuffer.allocate(3);

        //b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a byte buffer is always BIG_ENDIAN.
        b.put((byte) speed);
        b.put((byte) direction);
        b.put((byte) '\n');
        byte[] dataToSendToArduino = b.array();
        Log.i(TAG, " data to send to arduino " + dataToSendToArduino.toString());

        if (arduino != null && arduino.isOpened()) {
            arduino.send(dataToSendToArduino);
        }

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
                break;
        }
    }

    private void startStreaming() {
        String receiverAddr = gameModel.getReceiverAddr().getValue();

    }

    private void stopStreaming() {

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
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (arduino != null) {
                    arduino.reopen();
                }
            }
        }, 3000);
    }


    @Override
    public boolean onLongClick(View v) {


        return true;
    }


    public void setDirection(Integer direction) {
        this.direction = direction;
        this.refreshArduino();
    }

    public void setSpeed(Integer speed) {
        this.speed = speed;
        this.refreshArduino();
    }
}