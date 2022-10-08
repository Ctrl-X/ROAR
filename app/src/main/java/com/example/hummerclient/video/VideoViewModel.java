package com.example.hummerclient.video;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.nio.ByteBuffer;

public class VideoViewModel extends ViewModel {

    private final MutableLiveData<Integer> mSelectedCameraId;
    private final MutableLiveData<ByteBuffer> mImageBuffer;

    public VideoViewModel() {
        mImageBuffer = new MutableLiveData<>();
        mImageBuffer.setValue(null);
        mSelectedCameraId = new MutableLiveData<>();
        mSelectedCameraId.setValue(0);
    }



    public void setImageBuffer(ByteBuffer buffer) {
        mImageBuffer.setValue(buffer);
    }

    public ByteBuffer getImageBuffer() {
        return mImageBuffer.getValue();
    }

    public void selectNextCameraId() {
        mSelectedCameraId.setValue(mSelectedCameraId.getValue()+1);
    }

    public LiveData<Integer> getSelectedCameraId() {
        return mSelectedCameraId;
    }

    // TODO: Implement the ViewModel
}