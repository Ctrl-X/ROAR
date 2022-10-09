package com.example.hummerclient.networking;

public enum UDP_PORT {

    ROVER(2000),
    REMOTE_CONTROLLER(2001),

    CLIENT_RTP_RCV(25001), /* MANETTE */
    CLIENT_RTCP_RCV_PORT(19001),  /* MANETTE */

    SERVER_RTSP_RCV_PORT(19000); // ROVER

    private final int portNum;

    private UDP_PORT(int port) {
        this.portNum = port;
    }

    public int getValue() {
        return portNum;
    }
}
