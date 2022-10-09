package com.example.hummerclient.networking;

import android.util.Log;

import com.example.hummerclient.ui.UIRunnerInterface;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpTransmitter extends Thread {

    public static final int MAX_UDP_DATAGRAM_LEN = 100;

    private final TransmitterType type;
    private final int port;

    private String lastMessage = null;
    private UIRunnerInterface uiRunner;

    private String messageToSend;
    private InetAddress destAddress;

    public UdpTransmitter(TransmitterType type,int port, UIRunnerInterface obj) {
        this.type = type;
        this.uiRunner = obj;
        this.messageToSend = null;
        this.port = port;

//        if (isServer) {
//            // The server need to receive data on server port and send data to client port
//            receiveOnPort = REMOTE_CONTROLLER_PORT;
//            sendOnPort = ROVER_PORT;
//        } else {
//            // The other way for Client
//            receiveOnPort = ROVER_PORT;
//            sendOnPort = REMOTE_CONTROLLER_PORT;
//        }
    }

    public void run() {
        String message = null;
        byte[] lmessage = new byte[MAX_UDP_DATAGRAM_LEN];

        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
            if (TransmitterType.RECEIVER.equals(this.type)) {
                Log.i("ROAR", "Listening on UDP port " + port + " ...");
            }else{
                Log.i("ROAR",   "UDP Sender Ready !");

            }

            while (!this.isInterrupted()) {
                if (TransmitterType.RECEIVER.equals(this.type)) {
                    DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);
                    socket.receive(packet);
                    lastMessage = new String(lmessage, 0, packet.getLength());
                    this.uiRunner.runOnUI();
                } else {
                    if (messageToSend != null) {
                        Log.i("ROAR", "Sending '" + messageToSend + "' to " + this.destAddress + ":" + port);
                        byte[] buffer = messageToSend.getBytes();
                        DatagramPacket packet = new DatagramPacket(
                                buffer, buffer.length, this.destAddress, port);
                        socket.send(packet);
                        lastMessage = messageToSend;
                        messageToSend = null;
                        this.uiRunner.runOnUI();
                    }
                }


            }
        } catch (
                Throwable e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                if (socket.isConnected()) {
                    socket.disconnect();
                }
                socket.close();
            }
        }

    }

    public synchronized void send(String message, InetAddress destinationAddress) {
        this.messageToSend = message;
        this.destAddress = destinationAddress;
//        this.notify();
    }

    public void kill() {
        if (this.isAlive()) {
            this.interrupt();
        }
    }

    public synchronized String getLastMessage() {
        return lastMessage;
    }
}
