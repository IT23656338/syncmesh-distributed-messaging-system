package com.dms.common.model;

public class NodeInfo {
    private String id;
    private String host;
    private int port;

    public NodeInfo() {}
    public NodeInfo(String id, String host, int port) { this.id = id; this.host = host; this.port = port; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
