package com.example.hummerclient.networking;

public enum TransmitterType {
    EMITTER("Emitter"),
    RECEIVER("Receiver");

    private final String mType;

    TransmitterType(String type) {
        this.mType = type;
    }

    public String getValue() {
        return this.mType;
    }
}
