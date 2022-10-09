package com.example.hummerclient.video;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.nio.ByteBuffer;

public class VideoViewModel extends ViewModel {

    private final MutableLiveData<Integer> mSelectedCameraId;
    private final MutableLiveData<byte[]> mImageBuffer;

    private Boolean hasImage = false;

    public VideoViewModel() {
        mImageBuffer = new MutableLiveData<>();
        mImageBuffer.setValue(null);
        mSelectedCameraId = new MutableLiveData<>();
        mSelectedCameraId.setValue(0);
    }


    public synchronized void setImageBuffer(byte[] buffer) {
//        byte[] arr;
//        if (buffer == null) {
//            arr = new byte[0];
//        } else {
//            buffer.flip();
//            arr = new byte[buffer.remaining()];
//            buffer.get(arr);
//        }

        mImageBuffer.setValue(buffer);
        hasImage = true;
    }

    public synchronized LiveData<byte[]> getImageBuffer() {
//        ByteBuffer buf = mImageBuffer.getValue();
//        byte[] arr;
//        if (buf == null) {
//            arr = new byte[0];
//        } else {
//            buf.flip();
//            arr = new byte[buf.remaining()];
//            buf.get(arr);
//        }
        return mImageBuffer;
    }

    public void selectNextCameraId() {
        mSelectedCameraId.setValue(mSelectedCameraId.getValue() + 1);
    }

    public LiveData<Integer> getSelectedCameraId() {
        return mSelectedCameraId;
    }

    public boolean hasImage() {
        return hasImage;
    }

    // TODO: Implement the ViewModel
}