package com.example.hummerclient.game;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.text.TextUtils;
import android.util.Log;

import com.example.hummerclient.MotorActionnable;
import com.example.hummerclient.networking.DataSender;
import com.example.hummerclient.networking.TransmitterType;
import com.example.hummerclient.networking.UdpTransmitter;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public abstract class BaseFragment extends Fragment implements MotorActionnable{
    protected static String TAG = "ROAR";

    protected GameModel gameModel;

    protected UdpTransmitter dataReceiver;
    private DataSender dataSender;

    public BaseFragment() {
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gameModel = new ViewModelProvider(requireActivity()).get(GameModel.class);

    }

    @Override
    public void onPause() {
        super.onPause();
        stopUdpTransmission();
    }

    @Override
    public void onResume() {
        super.onResume();
        startUdpTransmission();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopUdpTransmission();
    }

    protected void startUdpTransmission() {

        stopUdpTransmission();

        dataReceiver = this.buildDataReceiver();
        dataSender = this.buildDataSender();

        gameModel.getMyAddr().observe(this, dataSender::setMyIpAddress);
        gameModel.getReceiverAddr().observe(this, dataSender::setReceiverAddress);

        dataReceiver.start();
        dataSender.start();
    }

    abstract UdpTransmitter buildDataReceiver();

    protected UdpTransmitter buildDataReceiver(int port) {
        return new UdpTransmitter(TransmitterType.RECEIVER, port, () -> getActivity().runOnUiThread(() -> {
            if (dataReceiver != null) {
                String message = dataReceiver.getLastMessage();
                if (!TextUtils.isEmpty(message)) {
                    Log.i(TAG, "received : " + message);
                    String[] datas = message.split(DataSender.dataDelimiter);
                    processReceivedData(datas);
                }
            }
        }));
    }

    protected abstract void processReceivedData(String[] datas);


    protected abstract DataSender buildDataSender();

    protected Runnable sendedMessage = new Runnable() {
        public void run() {

            if (dataSender != null) {
                String message = dataSender.getLastMessage();
                if (!TextUtils.isEmpty(message)) {
                    gameModel.setStatus(message);
                }
            }
        }
    };

    protected void stopUdpTransmission() {


        if (dataReceiver != null) {
            dataReceiver.kill();
            dataReceiver = null;
        }
        if (dataSender != null) {
            dataSender.kill();
            dataSender = null;
        }
    }



    //################ MotorActionnable INTERFACE IMPLEMENTATION ###############
    @Override
    public void changeSpeed(int newSpeed) {
        Log.i(TAG, "New speed : " + newSpeed);
        gameModel.setSpeed(newSpeed);
    }

    @Override
    public void changeDirection(int newDirection) {
        Log.i(TAG, "New direction : " + newDirection);
        gameModel.setDirection(newDirection);
    }
}