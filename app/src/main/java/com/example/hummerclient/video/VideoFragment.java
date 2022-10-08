package com.example.hummerclient.video;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.StateCallback;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.hummerclient.MainActivity;
import com.example.hummerclient.databinding.FragmentVideoBinding;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class VideoFragment extends Fragment {
    private static String TAG = "ROAR";

    private static final String ARG_IS_EMITTER = "isEmitter";
    private static int MAX_RES = 1080;

    // TODO : look at https://www.freecodecamp.org/news/android-camera2-api-take-photos-and-videos/
    // To display camera

    private VideoViewModel mVideoModel;

    private FragmentVideoBinding binding;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private String cameraId;

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private CameraManager cameraManager;
    private ImageReader imageReader;
    private SurfaceTexture surfaceTexture;
    private Surface previewSurface;
    private CaptureRequest.Builder captureRequestBuilder;
    private boolean cameraIsOpen;
    private WindowManager windowManager;
    private CameraDevice currentCameraDevice;
    private MainActivity mainActivity;

    private Boolean mIsEmitter = true;
    private TextureView textureView;

    public static VideoFragment newInstance() {
        return new VideoFragment();
    }

    public static VideoFragment newInstance(Boolean isEmitter) {
        VideoFragment fragment = new VideoFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_EMITTER, isEmitter);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mIsEmitter = getArguments().getBoolean(ARG_IS_EMITTER);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        mVideoModel = new ViewModelProvider(this).get(VideoViewModel.class);

        mainActivity = (MainActivity) this.getActivity();
        root.setClickable(true);
        textureView = binding.textureView;
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        textureView.setClickable(true);
        textureView.setOnTouchListener(new View.OnTouchListener() {
            boolean firstTouch = false;
            long time;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == event.ACTION_DOWN) {
                    if (firstTouch && (System.currentTimeMillis() - time) <= 300) {
                        firstTouch = false;
                        mVideoModel.selectNextCameraId();
                    } else {
                        firstTouch = true;
                        time = System.currentTimeMillis();
                        mainActivity.toggle();
                        return false;
                        //return false;Use this if you dont want to call default onTouchEvent()
                    }
                }
                return false;
            }
        });


        mVideoModel.getSelectedCameraId().observe(getViewLifecycleOwner(), integer -> {
            stopCamera();
            selectCamera();
        });

        windowManager = this.getActivity().getWindowManager();

        // Request camera permissions
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
                selectCamera();
            } else {
                Toast.makeText(this.getContext(),
                                "Vous n'avez pas accès à la vidéo LIVE. Dommage !",
                                Toast.LENGTH_LONG)
                        .show();
            }
        });

        checkPermission();
        return root;
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        startBackgroundThread();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        try {
            stopBackgroundThread();
            stopCamera();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this.getContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            selectCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Toast.makeText(this.getContext(),
                            "La camera permet de voir le LIVE feed du RoaR",
                            Toast.LENGTH_LONG)
                    .show();
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
        return true;
    }


    public void selectCamera() {
        if (!mIsEmitter) {
            // We don't use the camera of the receiver
            return;
        }
        if (cameraId == null) {
            String[] cameraIds;
            try {
                cameraManager = (CameraManager) this.getContext().getSystemService(Context.CAMERA_SERVICE);
                cameraIds = cameraManager.getCameraIdList();
                // on recupere la camera en fonction de son index, en bouclant avec le modulo (selectedCameraId peut depasser le nbr de cameraIds
                int selectedId = mVideoModel.getSelectedCameraId().getValue() % cameraIds.length;
                cameraId = cameraIds[selectedId];

//                CameraCharacteristics cameraCharacteristics;
//                for (String camId : cameraIds) {
//
//
//                    try {
//                        cameraCharacteristics = cameraManager.getCameraCharacteristics(camId);
//                        //We want to choose the rear facing camera instead of the front facing one
//                        if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
//                            continue;
//                        cameraId = camId;
//                        break;
//                    } catch (CameraAccessException e) {
//                        e.printStackTrace();
//                        continue;
//                    }
//                }
                startCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

    }

    @SuppressLint("MissingPermission")
    public void startCamera() {
        if (!mIsEmitter) {
            // We don't use the camera of the receiver
            return;
        }

        if (!cameraIsOpen) {
//            Log.i(TAG, "startCamera : " + cameraId);
            try {
                if (cameraId != null && surfaceTexture != null) {
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

                    Size[] sizeList = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(ImageFormat.JPEG);

//                    for (int i = 0; i < sizeList.length; i++) {
//                        Size size = sizeList[i];
//                        Log.i(TAG, "size "+i+" : "+size.getWidth()+"(w) x "+size.getHeight()+"(h) ");
//                    }

                    // Get the bigger width/height for the preview
                    Size previewSize = Arrays.stream(sizeList)
                            .filter(s -> s.getWidth() <= MAX_RES)
                            .max((size, previous) -> size.getHeight() * size.getWidth())
                            .get();
                    Log.i(TAG, "preview size : " + previewSize.getWidth() + "(w) x " + previewSize.getHeight() + "(h) ");

                    surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                    previewSurface = new Surface(surfaceTexture);

                    float ratio = ((float) textureView.getWidth()) / previewSize.getWidth();
                    int height = (int) (ratio * previewSize.getHeight());
                    textureView.setMinimumHeight(height);

                    imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
                    imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

                    cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
                    cameraIsOpen = true;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }
    }

    private void stopCamera() {
        if (cameraId != null) {
            // TODO

            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }

            if (currentCameraDevice != null) {
                currentCameraDevice.close();
                currentCameraDevice = null;
            }
            cameraIsOpen = false;
            cameraId = null;
        }
    }

    private void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread("CameraVideoThread");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(
                backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() throws InterruptedException {
        backgroundHandlerThread.quitSafely();
        backgroundHandlerThread.join();
    }

    private ByteBuffer latestImageBuffer;
    ImageReader.OnImageAvailableListener onImageAvailableListener = reader -> {
//        Log.i(TAG, "onImageAvailableListener -> acquireLatestImage");
        Image image = reader.acquireLatestImage();
        latestImageBuffer = image.getPlanes()[0].getBuffer();
        updateImageOnUiThread();
        image.close();
        // TODO : Faire la séparation client / server :
        //  le client doit demarrer la camera et envoyer le flux dans mVideoModel et l'envoyer en UDP ensuite
        // le client ne doit pas forcement afficher le flux video sur le surfaceView
        // le serveur doit receptionner le flux UDP et l'afficher dans la surfaceView
        // le serveur doit aussi envoyer le changer d'index de la caméra pour switcher de caméra
        // le serveur ne doit pas demarrer la camera
        // regler le probleme de rottion de la camera
    };


    private void updateImageOnUiThread() {
        this.getActivity().runOnUiThread(() -> mVideoModel.setImageBuffer(latestImageBuffer));
    }

    SurfaceTextureListener surfaceTextureListener = new SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            surfaceTexture = surface;
            startCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            stopCamera();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
//            surfaceTexture = surface;
//            stopCamera();
//            startCamera();
        }
    };


    StateCallback cameraStateCallback = new StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            try {
                SparseIntArray orientations = new SparseIntArray(4);
                orientations.append(Surface.ROTATION_0, 0);
                orientations.append(Surface.ROTATION_90, 90);
                orientations.append(Surface.ROTATION_180, 180);
                orientations.append(Surface.ROTATION_270, 270);

                currentCameraDevice = cameraDevice;
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

                // TODO on pourra retirer la previewSurface quand tout fonctionnera
                captureRequestBuilder.addTarget(previewSurface);
                captureRequestBuilder.addTarget(imageReader.getSurface());


                int rotation = windowManager.getDefaultDisplay().getRotation();
                Log.i(TAG, "rotation " + rotation);

                captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientations.get(rotation));

                // TODO : idem ici il faut plug uniquement imageReader
                cameraDevice.createCaptureSession(List.of(previewSurface, imageReader.getSurface()), captureStateCallback, null);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i(TAG, "camera onDisconnected ");
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            String errorMsg = null;
            switch (error) {
                case ERROR_CAMERA_DEVICE:
                    errorMsg = "Fatal (device)";
                    break;
                case ERROR_CAMERA_DISABLED:
                    errorMsg = "Device policy disabled camera";
                    break;
                case ERROR_CAMERA_IN_USE:
                    errorMsg = "Camera is already in use";
                    break;
                case ERROR_CAMERA_SERVICE:
                    errorMsg = "Fatal (service)";
                    break;
                case ERROR_MAX_CAMERAS_IN_USE:
                    errorMsg = "Maximum cameras in use";
                    break;
                default:
                    errorMsg = "Unkown error : " + error;
                    break;
            }

            Log.e(TAG, "Error when trying to connect camera : " + errorMsg);
            if (camera != null) {
                camera.close();
            }
        }
    };


    CameraCaptureSession.StateCallback captureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }

    };

}