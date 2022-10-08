package com.example.hummerclient.networking;

import com.example.hummerclient.ui.UIRunnerInterface;

import java.util.List;

public class DataSenderRover extends DataSender {


    public DataSenderRover(UIRunnerInterface callback) {
        super(UdpTransmitter.REMOTE_CONTROLLER_PORT, callback);
        setSleepTime(2000);
    }

    @Override
    String prepareData(List<String> datas) {
        // Le client envoi son IP au serveur en tout temps
        if (getMyIpAddress() != null) {
            datas.add("IP");
            datas.add(getMyIpAddress());
        }
        lastMessage = String.join(dataDelimiter, datas);

        return  lastMessage;
    }

}
