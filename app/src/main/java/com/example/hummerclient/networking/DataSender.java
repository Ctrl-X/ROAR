package com.example.hummerclient.networking;

import android.text.TextUtils;
import android.util.Log;

import com.example.hummerclient.ui.UIRunnerInterface;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public abstract class DataSender extends Thread {
    protected static String TAG = "ROAR";

    private InetAddress receiverAddress;
    private String myIpAddress;
    protected String previousMessage = null;

    public static final String dataDelimiter = "\t";
    private final UdpTransmitter mEmitter;

    private long sleepTime = 20;
    protected String lastMessage = null;

    public DataSender(int port, UIRunnerInterface callback) {
        this.mEmitter = new UdpTransmitter(TransmitterType.EMITTER, port, callback);
        this.mEmitter.start();
        Timer timer = new Timer();

    }


    public void run() {
        Log.i(TAG, "DataSender sync running... ");

//            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        List<String> datas = new ArrayList<>();
        try {
            String previousMessage = null;
            while (!this.isInterrupted()) {
                datas.clear();
                //Log.i("ROAR", output + " TO IP " + receiverAddress);
                if (receiverAddress != null) {
                    lastMessage = prepareData(datas);
                    if (!TextUtils.equals(lastMessage, previousMessage)) {
                        mEmitter.send(lastMessage, receiverAddress);
                    }
                }
                sleep(this.sleepTime);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    abstract String prepareData(List<String> datas);

    public void kill() {
        if (mEmitter != null) {
            mEmitter.kill();
        }
        if (this.isAlive()) {
            this.interrupt();
        }
    }

    public void setReceiverAddress(String strReceiverAddress) {
        InetAddress address = null;
        if (!TextUtils.isEmpty(strReceiverAddress)) {
            try {
                address = InetAddress.getByName(strReceiverAddress);
            } catch (UnknownHostException e1) {
                e1.printStackTrace();
            }
        }
        this.receiverAddress = address;
    }

    public void setMyIpAddress(String myIpAddress) {
        this.myIpAddress = myIpAddress;
    }

    public void setSleepTime(long sleepTime) {
        this.sleepTime = sleepTime;
    }

    public String getMyIpAddress() {
        return myIpAddress;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}