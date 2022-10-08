package com.example.hummerclient.game;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class GameModel extends ViewModel {

    private final MutableLiveData<String> mStatus;

    private final MutableLiveData<String> mMyAddr;
    private final MutableLiveData<String> mReceiverAddr;

    private final MutableLiveData<Boolean> mIsRemoteController;
    private final MutableLiveData<Boolean> mIsRunning;

    private final MutableLiveData<Integer> mSpeed;
    private final MutableLiveData<Integer> mDirection;


    public GameModel() {
        mStatus = new MutableLiveData<>();
        mStatus.setValue("Non connecté");

        mReceiverAddr = new MutableLiveData<>();
        mReceiverAddr.setValue("");
        mMyAddr = new MutableLiveData<>();
        mMyAddr.setValue("");


        mIsRemoteController = new MutableLiveData<>();
        mIsRemoteController.setValue(false);
        mIsRunning = new MutableLiveData<>();
        mIsRunning.setValue(false);

        mSpeed = new MutableLiveData<>();
        mSpeed.setValue(XboxPad.ZERO_SPEED);

        mDirection = new MutableLiveData<>();
        mDirection.setValue(XboxPad.ZERO_ANGLE);
    }

    public void setStatus(String status) {
        mStatus.setValue(status);
    }

    public LiveData<String> getStatus() {
        return mStatus;
    }


    public void setReceiverAddr(String receiverAddr) {
        mReceiverAddr.setValue(receiverAddr);
    }

    public LiveData<String> getReceiverAddr() {
        return mReceiverAddr;
    }

    public void setMyAddr(String myAddr) {
        mMyAddr.setValue(myAddr);
    }

    public LiveData<String> getMyAddr() {
        return mMyAddr;
    }


    public void setIsRemoteController(Boolean isServer) {
        mIsRemoteController.setValue(isServer);
    }

    public MutableLiveData<Boolean> getIsRemoteController() {
        return mIsRemoteController;
    }


    public void setIsRunning(Boolean isRunning) {
        mIsRunning.setValue(isRunning);
    }

    public MutableLiveData<Boolean> getIsRunning() {
        return mIsRunning;
    }

    public void setSpeed(Integer speed) {
        if (speed != null) {
            mSpeed.setValue(speed);
        }
    }

    public MutableLiveData<Integer> getSpeed() {
        return mSpeed;
    }

    public void setDirection(Integer direction) {
        if (direction != null) {

            mDirection.setValue(direction);
        }
    }

    public MutableLiveData<Integer> getDirection() {
        return mDirection;
    }


}