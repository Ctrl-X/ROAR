package com.example.hummerclient.networking;

import android.util.Log;

import com.example.hummerclient.ui.UIRunnerInterface;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpTransmitter extends Thread {
    public static final int CLIENT_PORT = 2000;
    public static final int SERVER_PORT = 2001;
    public static final int MAX_UDP_DATAGRAM_LEN = 100;

    private final TransmitterType type;
    private final int receiveOnPort;
    private final int sendOnPort;

    private String lastMessage = null;
    private UIRunnerInterface uiRunner;

    private String messageToSend;
    private InetAddress destAddress;

    public UdpTransmitter(TransmitterType type, Boolean isServer, UIRunnerInterface obj) {
        this.type = type;
        this.uiRunner = obj;
        this.messageToSend = null;
        if (isServer) {
            // The server need to receive data on server port and send data to client port
            receiveOnPort = SERVER_PORT;
            sendOnPort = CLIENT_PORT;
        } else {
            // The other way for Client
            receiveOnPort = CLIENT_PORT;
            sendOnPort = SERVER_PORT;
        }
    }

    public void run() {
        String message = null;
        byte[] lmessage = new byte[MAX_UDP_DATAGRAM_LEN];
        Log.i("ROAR", this.type.getValue() + " started... ");

        DatagramSocket socket = null;
        try {
            if (TransmitterType.RECEIVER.equals(this.type)) {
                Log.i("ROAR", "Listening on UDP port " + receiveOnPort + " ...");
                socket = new DatagramSocket(receiveOnPort);
            } else {
                socket = new DatagramSocket(sendOnPort);
            }
            while (!this.isInterrupted()) {
                if (TransmitterType.RECEIVER.equals(this.type)) {
                    DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);
                    socket.receive(packet);
                    lastMessage = new String(lmessage, 0, packet.getLength());
                    this.uiRunner.runOnUI();
                } else {
                    if (messageToSend != null) {
                        Log.i("ROAR", "Sending '" + messageToSend + "' to " + this.destAddress + ":" + sendOnPort);
                        byte[] buffer = messageToSend.getBytes();
                        DatagramPacket packet = new DatagramPacket(
                                buffer, buffer.length, this.destAddress, sendOnPort);
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
