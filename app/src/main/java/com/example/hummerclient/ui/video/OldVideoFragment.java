package com.example.hummerclient.ui.video;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.example.hummerclient.databinding.FragmentVideoBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class OldVideoFragment extends Fragment {

    private VideoViewModel mViewModel;

    private FragmentVideoBinding binding;
    TextView text_view;
    PreviewView view_finder;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    Executor executor;
    private long mLastAnalysisResultTime;
    private Camera mCamera;
    private MediaRecorder mediaRecorder;


    public static OldVideoFragment newInstance() {
        return new OldVideoFragment();
    }


//    public static VideoFragment newInstance(String param1, String param2) {
//        VideoFragment fragment = new VideoFragment();
//        Bundle args = new Bundle();
//        args.putString(ARG_PARAM1, param1);
//        args.putString(ARG_PARAM2, param2);
//        fragment.setArguments(args);
//        return fragment;
//    }

//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        if (getArguments() != null) {
//            mParam1 = getArguments().getString(ARG_PARAM1);
//            mParam2 = getArguments().getString(ARG_PARAM2);
//        }
//    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);

        View root = binding.getRoot();
//        view_finder = binding.viewFinder;
        text_view = binding.textView;

        // Request camera permissions
        requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        // Permission is granted. Continue the action or workflow in your
                        // app.
                        startCamera();
                    } else {
                        Toast.makeText(this.getContext(),
                                        "Vous n'avez pas accès à la vidéo LIVE. Dommage !",
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                });
        checkPermission();

        return root;    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        // TODO: Use the ViewModel
    }


    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this.getContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            startCamera();
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


    public void startCamera() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture
                = ProcessCameraProvider.getInstance(this.getContext());

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this.getContext()));
    }


    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        executor = Executors.newSingleThreadExecutor();

        QualitySelector qualitySelector = QualitySelector.fromOrderedList(
                new ArrayList<Quality>(Arrays.asList(Quality.HD, Quality.SD, Quality.FHD,Quality.UHD)),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD));

        Recorder recorder = new Recorder.Builder()
                .setExecutor(executor).setQualitySelector(qualitySelector)
                .build();
        VideoCapture<Recorder> videoCapture = VideoCapture.withOutput(recorder);


        // To analyse the picture
//        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
//                .setTargetResolution(new Size(224, 224))
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build();
//        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
//            @Override
//            public void analyze(@NonNull ImageProxy image) {
////                mViewModel.setImage(image.getImage());
//                int rotationDegrees = image.getImageInfo().getRotationDegrees();
//
//                long elapsedTime = SystemClock.elapsedRealtime();
//                long duration = elapsedTime - mLastAnalysisResultTime;
////                if(duration < 500) {
////                    image.close();
////                    return;
////                }
//
//                getActivity().runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        double fps;
//                        fps = 1000.f / duration;
//                        text_view.setText(String.format(Locale.US, "%.1f fps %d°", fps, rotationDegrees));
//                    }
//                });
//
//                mLastAnalysisResultTime = elapsedTime;
//                image.close();
//            }
//        });

        cameraProvider.unbindAll();
        mCamera = cameraProvider.bindToLifecycle((LifecycleOwner) this,
                cameraSelector, /*imageAnalysis,*/ preview,videoCapture);

        // get an image from the camera

        preview.setSurfaceProvider(view_finder.getSurfaceProvider());
    }


}