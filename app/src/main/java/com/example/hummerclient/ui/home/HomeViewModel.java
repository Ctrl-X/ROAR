package com.example.hummerclient.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.hummerclient.vehicule.VehiculeMotion;

public class HomeViewModel extends ViewModel {


    private final MutableLiveData<String> mStatus;

    private final MutableLiveData<String> mMyAddr;
    private final MutableLiveData<String> mReceiverAddr;

    private final MutableLiveData<Boolean> mIsServer;
    private final MutableLiveData<Boolean> mIsRunning;

    private final MutableLiveData<Integer> mSpeed;
    private final MutableLiveData<Integer> mDirection;


    public HomeViewModel() {
        mStatus = new MutableLiveData<>();
        mStatus.setValue("Non connecté");

        mReceiverAddr = new MutableLiveData<>();
        mReceiverAddr.setValue("");
        mMyAddr = new MutableLiveData<>();
        mMyAddr.setValue("");


        mIsServer = new MutableLiveData<>();
        mIsServer.setValue(false);
        mIsRunning = new MutableLiveData<>();
        mIsRunning.setValue(false);

        mSpeed = new MutableLiveData<>();
        mSpeed.setValue(VehiculeMotion.MOTOR_MAX_PWM / 2);

        mDirection = new MutableLiveData<>();
        mDirection.setValue(VehiculeMotion.SERVO_MAX_ANGLE / 2);
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


    public void setIsServer(Boolean isServer) {
        mIsServer.setValue(isServer);
    }


    public MutableLiveData<Boolean> getIsServer() {
        return mIsServer;
    }


    public void setIsRunning(Boolean isRunning) {
        mIsRunning.setValue(isRunning);
    }

    public MutableLiveData<Boolean> getIsRunning() {
        return mIsRunning;
    }

    public void setSpeed(Integer speed) {
        mSpeed.setValue(speed);
    }

    public MutableLiveData<Integer> getSpeed() {
        return mSpeed;
    }

    public void setDirection(Integer direction) {
        mDirection.setValue(direction);
    }

    public MutableLiveData<Integer> getDirection() {
        return mDirection;
    }


}