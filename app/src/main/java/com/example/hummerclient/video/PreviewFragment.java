package com.example.hummerclient.video;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;


import com.example.hummerclient.databinding.FragmentPreviewBinding;
import com.example.hummerclient.video.helper.CompareSizesByArea;
import com.example.hummerclient.video.helper.PreferenceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

//import us.yydcdut.camera2.DngImageSaver;
//import us.yydcdut.camera2.ImageSaver;
//import us.yydcdut.camera2.PreferenceHelper;
//import us.yydcdut.camera2.R;
//import us.yydcdut.camera2.view.AutoFitTextureView;

/**
 * Created by yuyidong on 15-1-8.
 */
public class PreviewFragment extends Fragment{
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_CAPTURE = 1;
    private static final int STATE_TRY_CAPTURE_AGAIN = 2;
    private static final int STATE_TRY_DO_CAPTURE = 3;
    private int mState = STATE_PREVIEW;
    private Handler mPreviewHandler;
    private HandlerThread mHandlerThread;
    private TextureView mPreviewView;
    private ImageReader mImageReader;
    private Size mPreviewSize;
    private String mCameraId;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mSession;
    private MediaActionSound mMediaActionSound;
    private Surface mSurface;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }



    public static PreviewFragment newInstance() {
        PreviewFragment fragment = new PreviewFragment();
        fragment.setRetainInstance(true);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        initShutter();
        FragmentPreviewBinding binding = FragmentPreviewBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        mPreviewView = binding.texture;

        return root;
    }

    private void initShutter() {
        mMediaActionSound = new MediaActionSound();
        mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
    }


    private void initLooper() {
        mHandlerThread = new HandlerThread("Camera_2");
        mHandlerThread.start();
        mPreviewHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onResume() {
        super.onResume();
        initLooper();
        if (mPreviewView.isAvailable()) {
            openCamera(mPreviewView.getWidth(), mPreviewView.getHeight());
        } else {
            mPreviewView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, DeviceStateCallback, mPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {

            String cameraId = PreferenceHelper.getCameraId(getActivity());
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size largest;
            if (PreferenceHelper.getCameraFormat(getActivity()).equals("JPEG")) {
                largest = Collections.max(Arrays.asList(map.getOutputSizes(android.graphics.ImageFormat.JPEG)), new CompareSizesByArea());
                initImageReader(largest, ImageFormat.JPEG);
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);
            } else {
                largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)), new CompareSizesByArea());
                initImageReader(largest, ImageFormat.RAW_SENSOR);
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);
            }


            mCameraId = cameraId;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
        }
    }

    private void initImageReader(Size size, int format) {
        mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), format, /*maxImages*/7);
        if (format == ImageFormat.JPEG) {
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mPreviewHandler);
        } else {
            mImageReader.setOnImageAvailableListener(mRawOnImageAvailableListener, mPreviewHandler);
        }
    }


    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
//            new Thread(new ImageSaver(reader)).start();
            mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
        }
    };
    //    private Image mRawImage;
    private ImageReader.OnImageAvailableListener mRawOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
//            mRawImage = reader.acquireNextImage();
//            takeDngPicture(mDngResult, reader);
//            mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
        }
    };

    private Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mPreviewView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mPreviewView.setTransform(matrix);
    }


    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private CameraDevice.StateCallback DeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            try {
                createCameraPreviewSession();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
            Toast.makeText(getActivity(), "onError,error--->" + error, Toast.LENGTH_SHORT).show();
        }
    };

    private void initSurface() {
        SurfaceTexture texture = mPreviewView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mSurface = new Surface(texture);
    }

    private void createCameraPreviewSession() throws CameraAccessException {
        initSurface();
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        mPreviewBuilder.addTarget(mSurface);
        mState = STATE_PREVIEW;
        mCameraDevice.createCaptureSession(Arrays.asList(mSurface, mImageReader.getSurface()), mSessionPreviewStateCallback, mPreviewHandler);
    }

    private CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            if (null == mCameraDevice) {
                return;
            }
            mSession = session;
            try {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                session.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mPreviewHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Activity activity = getActivity();
            if (null != activity) {
                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
            }
        }
    };


    private TotalCaptureResult mDngResult;
    private CameraCaptureSession.CaptureCallback mSessionDngCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.i("mSessionDngCaptureCallback", "mSessionDngCaptureCallback");
            mDngResult = result;
        }
    };
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            mSession = session;
            if (!PreferenceHelper.getCameraFormat(getActivity()).equals("DNG")) {
                checkState(result);
            }

        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            mSession = session;
            if (!PreferenceHelper.getCameraFormat(getActivity()).equals("DNG")) {
                checkState(partialResult);
            }
        }

        private void checkState(CaptureResult result) {
//            mFrameBitmap = mPreviewView.getBitmap();
//            mMainHandler.sendEmptyMessage(1);
            switch (mState) {
                case STATE_PREVIEW:
                    // NOTHING
                    break;
                case STATE_WAITING_CAPTURE:
                    int afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Log.i("checkState", "afState--->" + afState);
                    if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                            || CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState || CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        Log.i("checkState", "进来了一层,aeState--->" + aeState);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            Log.i("checkState", "进来了第二层");
                            mState = STATE_TRY_DO_CAPTURE;
                            doStillCapture();
                        } else {
                            mState = STATE_TRY_CAPTURE_AGAIN;
                            tryCaptureAgain();
                        }
                    }
                    break;
                case STATE_TRY_CAPTURE_AGAIN:
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_TRY_DO_CAPTURE;
                    }
                    break;
                case STATE_TRY_DO_CAPTURE:
                    aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_TRY_DO_CAPTURE;
                        doStillCapture();
                    }
                    break;
            }
        }

    };

    private void doStillCapture() {
        try {
            CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            mSession.stopRepeating();
            mSession.setRepeatingRequest(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
            }, mPreviewHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void tryCaptureAgain() {
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        try {
            mSession.capture(mPreviewBuilder.build(), mSessionCaptureCallback, mPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onPause() {
        super.onPause();
        mCameraOpenCloseLock.release();
        mCameraDevice.close();
        mCameraDevice = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}