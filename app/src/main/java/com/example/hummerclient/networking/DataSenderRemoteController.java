package com.example.hummerclient.networking;

import com.example.hummerclient.ui.UIRunnerInterface;
import com.example.hummerclient.game.XboxPad;

import java.util.List;

public class DataSenderRemoteController extends DataSender {

    private Integer speed = XboxPad.ZERO_SPEED;
    private Integer direction = XboxPad.ZERO_ANGLE;

    public DataSenderRemoteController(UIRunnerInterface callback) {
        super(UDP_PORT.ROVER, callback);

        setSleepTime(20);
    }

    @Override
    String prepareData(List<String> datas) {
        previousMessage = lastMessage;

        // Le serveur envoi les speeds et direction au client
        datas.add("MV");

        if (speed == null) {
            datas.add(XboxPad.ZERO_SPEED.toString());
        } else {
            datas.add(speed.toString());
        }
        if (direction == null) {
            datas.add(XboxPad.ZERO_ANGLE.toString());
        } else {
            datas.add(direction.toString());
        }

        return String.join(dataDelimiter, datas);
    }


    public void setDirection(Integer direction) {
        this.direction = direction;
    }

    public void setSpeed(Integer speed) {
        this.speed = speed;
    }
}
