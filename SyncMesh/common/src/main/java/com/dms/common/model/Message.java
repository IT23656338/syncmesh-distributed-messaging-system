package com.dms.common.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class Message implements Serializable {
    private String id;
    private String sender;
    private String receiver;
    private String payload;
    private Instant timestamp;
    private long lamport;
    private String originNodeId;
    private Map<String, Long> vectorClock = new HashMap<>();

    public Message() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public Message(String sender, String receiver, String payload) {
        this();
        this.sender = sender;
        this.receiver = receiver;
        this.payload = payload;
    }

    // getters and setters
    public String getId() { return id; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public long getLamport() { return lamport; }
    public void setLamport(long lamport) { this.lamport = lamport; }

    public String getOriginNodeId() { return originNodeId; }
    public void setOriginNodeId(String originNodeId) { this.originNodeId = originNodeId; }

    public Map<String, Long> getVectorClock() { return vectorClock; }
    public void setVectorClock(Map<String, Long> vectorClock) { this.vectorClock = vectorClock; }
}
