package com.example.hummerclient;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.hummerclient.arduino.Arduino;
import com.example.hummerclient.arduino.ArduinoListener;
import com.example.hummerclient.databinding.ActivityMainBinding;
import com.example.hummerclient.vehicule.VehiculeMotion;
import com.example.hummerclient.networking.IpChecker;
import com.example.hummerclient.networking.TransmitterType;
import com.example.hummerclient.networking.UdpTransmitter;
import com.example.hummerclient.ui.home.HomeViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements MotorActionnable, ArduinoListener {


    private ActivityMainBinding binding;
    private HomeViewModel homeViewModel;

    private Boolean isServer = false;
    private Boolean isRunning = false;
    private String myAddr = null;
    private String receiverAddr = null;

    private IpChecker ipChecker = null;
    private UdpTransmitter mReceiver;
    private UdpTransmitter mEmitter;

    private DataSyncRunner ping;
    VehiculeMotion vehiculeMotion = null;

    private String dataDelimiter = "\t";

    private Arduino arduino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);


        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }


        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        homeViewModel.getIsServer().observe(this, isServer -> this.isServer = isServer);
        homeViewModel.getReceiverAddr().observe(this, receiverAddr -> this.receiverAddr = receiverAddr);
        homeViewModel.getIsRunning().observe(this, isRunning -> {
            if (this.isRunning != isRunning) {
                this.isRunning = isRunning;
                if (isRunning) {
                    startUdpTransmission();
                } else {
                    stopUdpTransmission();
                }
            }
        });


        vehiculeMotion = new VehiculeMotion(this);
    }

    private void startUdpTransmission() {


        stopUdpTransmission();
        // TODO : Change the way message are received/ send
        mReceiver = new UdpTransmitter(TransmitterType.RECEIVER, isServer, () -> runOnUiThread(receivedMessage));
        mEmitter = new UdpTransmitter(TransmitterType.EMITTER, isServer, () -> runOnUiThread(sendedMessage));
        mReceiver.start();
        mEmitter.start();

        Log.i("ROAR", this.isServer ? " Server" : "Client" + " is now Running...");

        if (isServer) {
            homeViewModel.setStatus("En attente de client...");
        } else {
            // The client need to ping the server to broadcast his IP address
            homeViewModel.setStatus("En attente du serveur...");
            arduino = new Arduino(this);
            arduino.setArduinoListener(this);
        }
        ping = new DataSyncRunner();
        ping.start();
    }

    private void stopUdpTransmission() {

        if (arduino != null) {
            arduino.unsetArduinoListener();
            arduino.close();
            arduino = null;
        }
        if (ping != null) {
            ping.kill();
        }

        if (mReceiver != null) {
            mReceiver.kill();
            mReceiver = null;
        }
        if (mEmitter != null) {
            mEmitter.kill();
            mEmitter = null;
        }


    }


    protected void onResume() {
        super.onResume();

        homeViewModel.setStatus("Recherche de l'adresse IP...");
        ipChecker = new IpChecker(() -> runOnUiThread(updateIpAddress));
        ipChecker.start();

        if (isRunning) {
            startUdpTransmission();
        }


    }

    protected void onPause() {
        super.onPause();
        stopUdpTransmission();

        if (ipChecker != null && ipChecker.isAlive()) {
            ipChecker.interrupt();
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        boolean result = vehiculeMotion.onGenericMotionEvent(event);
        if (result) {
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean result = vehiculeMotion.onKeyDown(keyCode, event);
        if (result) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean result = vehiculeMotion.onKeyUp(keyCode, event);
        if (result) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private Runnable receivedMessage = new Runnable() {
        public void run() {
            if (mReceiver != null) {
                String message = mReceiver.getLastMessage();
                Log.i("ROAR", "received : " + message);
                if (!TextUtils.isEmpty(message)) {
                    String[] datas = message.split(dataDelimiter);
                    if (isServer) {
                        processClientResponse(datas);
                    } else {
                        processServerResponse(datas);
                    }
                }
            }
        }
    };

    /**
     * Data received from the server to be proceed in the client
     *
     * @param dataSet contains [key, values...] data like speed, direction and so on
     */
    private void processServerResponse(String[] dataSet) {
        if (dataSet.length < 2) {
            return;
        }
        String action = dataSet[0];
        switch (action) {
            case "MV":
                //Move action
                String speed = dataSet[1];
                String direction = dataSet[2];
                int intSpeed = Integer.parseInt(speed);
                int intDirection = Integer.parseInt(direction);
                changeSpeed(intSpeed);
                changeDirection(intDirection);
                ByteBuffer b = ByteBuffer.allocate(3);

                //b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a byte buffer is always BIG_ENDIAN.
                b.put((byte) intSpeed);
                b.put((byte) intDirection);
                b.put((byte) '\n');
                byte[] dataToSendToArduino = b.array();
                Log.i("ROAR", " data to send to arduino " + dataToSendToArduino.toString());

                if (arduino != null && arduino.isOpened()) {
                    arduino.send(dataToSendToArduino);
                }
                break;
        }
    }

    /**
     * Response received from the client
     *
     * @param dataSet contains [key,values...] data like IP address
     */
    private void processClientResponse(String[] dataSet) {
        if (dataSet.length < 2) {
            return;
        }
        String action = dataSet[0];
        switch (action) {
            case "IP":
                // We need to set the client IP for the serveur to send message
                String value = dataSet[1];
                homeViewModel.setReceiverAddr(value);
                Log.i("ROAR", " updated client IP to " + value);
                break;
        }
    }


    private Runnable sendedMessage = new Runnable() {
        public void run() {
            if (mEmitter != null) {
                String message = mEmitter.getLastMessage();
                if (!TextUtils.isEmpty(message)) {
//                    Log.i("HUMMER sended : ", message);
                    homeViewModel.setStatus(message);
                }
            }
        }
    };


    private Runnable updateIpAddress = new Runnable() {
        public void run() {
            String myIpAddr = ipChecker.getIpAddress();
            myAddr = myIpAddr;
            homeViewModel.setMyAddr(myIpAddr);
            homeViewModel.setStatus("Mon address ip est " + myAddr);
            Log.i("ROAR", "my ip address is now " + ipChecker.getIpAddress());
        }
    };

    @Override
    public void changeSpeed(int newSpeed) {
        Log.i("ROAR", "New speed : " + newSpeed);
        homeViewModel.setSpeed(newSpeed);
    }

    @Override
    public void changeDirection(int newDirection) {
        Log.i("ROAR", "New direction : " + newDirection);
        homeViewModel.setDirection(newDirection);
    }


    @Override
    public void onArduinoAttached(UsbDevice device) {
        Log.i("ROAR", "arduino attached : " + device.getDeviceName());
        arduino.open(device);
    }

    @Override
    public void onArduinoDetached() {
        Log.i("ROAR", "arduino detached");

    }

    @Override
    public void onArduinoMessage(byte[] bytes) {
        Log.i("ROAR", "Got data from Arduino : " + new String(bytes));
    }

    @Override
    public void onArduinoOpened() {
        Log.i("ROAR", "onArduinoOpened ");
//        String str = "arduino opened...";
//        arduino.send(str.getBytes());
    }

    @Override
    public void onUsbPermissionDenied() {
        Log.i("ROAR", "Permission denied. Attempting again in 3 sec...");
//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                if (arduino != null) {
////                    arduino.reopen();
//                }
//            }
//        }, 3000);
    }


    private class DataSyncRunner extends Thread {
        private InetAddress receiverAddress;

        public void run() {
            Log.i("ROAR", "Data sync started... ");

//            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            List<String> datas = new ArrayList<>();
            try {
                String previousMessage = null;
                while (!this.isInterrupted()) {
                    if (receiverAddress == null) {
                        receiverAddress = getReceiverAddr();
                    }
                    if (mEmitter != null && receiverAddress != null) {
                        datas.clear();
                        if (isServer) {
                            // Le serveur envoi les speeds et direction au client
                            datas.add("MV");
                            datas.add(homeViewModel.getSpeed().getValue().toString());
                            datas.add(homeViewModel.getDirection().getValue().toString());

                        } else {

                            // Le client envoi son IP au serveur en tout temps
                            if (myAddr != null) {
                                datas.add("IP");
                                datas.add(myAddr);
                            }
                        }
                        if (!datas.isEmpty()) {
                            String message = String.join(dataDelimiter, datas);

                            if (!TextUtils.equals(message, previousMessage)) {
                                mEmitter.send(message, receiverAddress);
                                previousMessage = message;
                            }
                        }
                    }

                    if (isServer) {
                        sleep(20);
                        Log.i("ROAR", "Server sending data... ");
                    } else {
                        sleep(2000);
                        Log.i("ROAR", "Client sending data... ");
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        public void kill() {
            if (this.isAlive()) {
                this.interrupt();
            }
        }
    }

    private InetAddress getReceiverAddr() {
        InetAddress receiverAddress = null;
        if (!TextUtils.isEmpty(receiverAddr)) {
            try {
                receiverAddress = InetAddress.getByName(receiverAddr);
            } catch (UnknownHostException e1) {
                e1.printStackTrace();
            }
        }
        return receiverAddress;
    }

}