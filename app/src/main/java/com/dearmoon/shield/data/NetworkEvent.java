package com.dearmoon.shield.data;

import org.json.JSONException;
import org.json.JSONObject;

public class NetworkEvent extends TelemetryEvent {
    private String destinationIp;
    private int destinationPort;
    private String protocol;
    private long bytesSent;
    private long bytesReceived;
    private int appUid;

    public NetworkEvent(String destIp, int destPort, String proto, long sent, long received, int uid) {
        super("NETWORK");
        this.destinationIp = destIp;
        this.destinationPort = destPort;
        this.protocol = proto;
        this.bytesSent = sent;
        this.bytesReceived = received;
        this.appUid = uid;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = getBaseJSON();
        json.put("destinationIp", destinationIp);
        json.put("destinationPort", destinationPort);
        json.put("protocol", protocol);
        json.put("bytesSent", bytesSent);
        json.put("bytesReceived", bytesReceived);
        json.put("appUid", appUid);
        return json;
    }
}
