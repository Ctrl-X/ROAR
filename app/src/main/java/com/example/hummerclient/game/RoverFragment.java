package com.example.hummerclient.game;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.hummerclient.R;
import com.example.hummerclient.arduino.Arduino;
import com.example.hummerclient.arduino.ArduinoListener;
import com.example.hummerclient.networking.DataSender;
import com.example.hummerclient.networking.DataSenderRover;
import com.example.hummerclient.networking.UdpTransmitter;
import com.example.hummerclient.ui.UIRunnerInterface;

import java.nio.ByteBuffer;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class RoverFragment extends BaseFragment implements  ArduinoListener {

    private Arduino arduino;


    public RoverFragment() {
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
        return inflater.inflate(R.layout.fragment_rover, container, false);
    }


    @Override
    protected void startUdpTransmission() {
        super.startUdpTransmission();

        gameModel.setStatus("En attente de la manette...");
        arduino = new Arduino(this.getContext());
        arduino.setArduinoListener(this);
        Log.i(TAG, "Rover is now Running...");
    }


    @Override
    protected void stopUdpTransmission() {
        super.stopUdpTransmission();
        if (arduino != null) {
            arduino.unsetArduinoListener();
            arduino.close();
            arduino = null;
        }
    }

    @Override
    UdpTransmitter buildDataReceiver() {
        return buildDataReceiver(UdpTransmitter.ROVER_PORT);
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


}