package com.example.hummerclient.game;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.hummerclient.databinding.FragmentRemoteControllerBinding;
import com.example.hummerclient.networking.DataSender;
import com.example.hummerclient.networking.DataSenderRemoteController;
import com.example.hummerclient.networking.UDP_PORT;
import com.example.hummerclient.networking.UdpTransmitter;
import com.example.hummerclient.networking.VideoClient;
import com.example.hummerclient.networking.VideoServer;
import com.example.hummerclient.ui.UIRunnerInterface;
import com.example.hummerclient.video.VideoViewModel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class RemoteControllerFragment extends BaseFragment implements TextureView.SurfaceTextureListener {


    private FragmentRemoteControllerBinding binding;
    private VideoViewModel mVideoModel;
    private VideoClient videoClient;
    private String previousReceiveAddr = null;
    private TextureView textureView;
    private MediaPlayer mMediaPlayer;

    public RemoteControllerFragment() {
        //For remote controller :
        // ingress by remote controller port
        // egress : send on the ROVER port
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        binding = FragmentRemoteControllerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        textureView = binding.texture;
        mMediaPlayer = new MediaPlayer();

        textureView.setSurfaceTextureListener(this);


        // TODO : videoClient A mettre dans un thread séparé
        gameModel.getReceiverAddr().observe(getViewLifecycleOwner(), receiveAddr -> {
                    if (!TextUtils.isEmpty(receiveAddr) && receiveAddr != previousReceiveAddr) {
                        previousReceiveAddr = receiveAddr;
                        if(videoClient != null){
                            videoClient.kill();
                        }
                        videoClient = new VideoClient(mVideoModel,receiveAddr, UDP_PORT.SERVER_RTSP_RCV_PORT);
                        try {
                            videoClient.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
        );



        return root;
    }

    @Override
    protected void startUdpTransmission() {
        super.startUdpTransmission();

        gameModel.setStatus("En attente du Rover...");
        Log.i(TAG, "Remote Controller is now Running...");


    }

    @Override
    protected void stopUdpTransmission() {
        super.stopUdpTransmission();

        if(videoClient != null){
            videoClient.kill();
        }
    }


    @Override
    UdpTransmitter buildDataReceiver() {
        return buildDataReceiver(UDP_PORT.REMOTE_CONTROLLER);
    }

    @Override
    protected DataSender buildDataSender() {
        UIRunnerInterface callback = () -> getActivity().runOnUiThread(sendedMessage);

        DataSenderRemoteController dataSyncRunner = new DataSenderRemoteController(callback);

        gameModel.getSpeed().observe(this, dataSyncRunner::setSpeed);
        gameModel.getDirection().observe(this, dataSyncRunner::setDirection);

        return dataSyncRunner;
    }


    /**
     * Response received from the rover
     *
     * @param dataSet contains [key,values...] data like IP address
     */
    @Override
    protected void processReceivedData(String[] dataSet) {
        if (dataSet.length < 2) {
            return;
        }
        String action = dataSet[0];
        switch (action) {
            case "IP":
                // We received the IP addresse of the rover, update it
                String value = dataSet[1];
                gameModel.setReceiverAddr("192.168.0.194");
//                homeViewModel.setReceiverAddr(value);
                Log.i(TAG, " updated Rover IP to " + value);
                break;
        }
    }


    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        mMediaPlayer.setSurface(new Surface(surface));
        mMediaPlayer.prepareAsync();
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mMediaPlayer.start();
            }
        });
        // TODO : refaire toute la preview en utilisant https://github.com/pedroSG94/rtmp-rtsp-stream-client-java
        // https://github.com/pedroSG94/rtmp-rtsp-stream-client-java/tree/master/app/src/main/java/com/pedro/rtpstreamer/texturemodeexample




//        mVideoModel.getImageBuffer().observe(getViewLifecycleOwner(), byteArray -> {
//                    InputStream is = new ByteArrayInputStream(byteArray);
//            mMediaPlayer.setDa.setDataSource(is.get);
//                    BufferedImage newBi = ImageIO.read(is);
//                }
//        );
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }


}