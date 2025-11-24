package com.dms.server.service;

import com.dms.common.model.Message;
import com.dms.common.model.NodeInfo;
import com.dms.server.zookeeper.ZooKeeperConnector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ReplicationService {
    private final List<String> replicas = new CopyOnWriteArrayList<>();
    private final ZooKeeperConnector connector;
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper M = new ObjectMapper().registerModule(new JavaTimeModule());
    private volatile boolean partitionMode = false;
    private final List<Message> sentLog = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_SIZE = 500;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ReplicationService(ZooKeeperConnector connector) {
        this.connector = connector;
        
        // Configure RestTemplate with timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000);   // 10 seconds
        http.setRequestFactory(factory);
    }

    @PostConstruct
    private void initMembershipWatch() {
        try {
            System.out.println("Initializing membership watch...");
            refreshReplicas();
            ZooKeeper zk = connector.getZooKeeper();
            String serversPath = connector.getServersPath();
            zk.getChildren(serversPath, (Watcher) event -> {
                System.out.println("ZooKeeper membership change detected: " + event.getType());
                try { 
                    refreshReplicas(); 
                } catch (Exception e) {
                    System.out.println("Error refreshing replicas after membership change: " + e.getMessage());
                }
            });
            System.out.println("Membership watch initialized successfully");
            
            // Also schedule periodic refresh every 10 seconds as backup
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    refreshReplicas();
                } catch (Exception e) {
                    System.out.println("Periodic replica refresh failed: " + e.getMessage());
                }
            }, 10, 10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            System.out.println("Failed to initialize membership watch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void refreshReplicas() throws Exception {
        ZooKeeper zk = connector.getZooKeeper();
        String serversPath = connector.getServersPath();
        List<String> children = zk.getChildren(serversPath, false);
        List<String> current = new ArrayList<>();
        String selfId = connector.getNodeId();
        
        System.out.println("Found " + children.size() + " servers in ZooKeeper: " + children);
        System.out.println("Current server ID: " + selfId);
        
        for (String c : children) {
            if (c.equals(selfId)) {
                System.out.println("Skipping self: " + c);
                continue;
            }
            byte[] data = zk.getData(serversPath + "/" + c, false, null);
            NodeInfo ni = M.readValue(data, NodeInfo.class);
            String url = "http://" + ni.getHost() + ":" + ni.getPort();
            current.add(url);
            System.out.println("Added replica: " + c + " -> " + url);
        }
        
        List<String> old = new ArrayList<>(replicas);
        replicas.clear();
        replicas.addAll(current);
        System.out.println("Replica membership updated: " + replicas);
        
        // replay to new nodes
        List<String> added = new ArrayList<>(current);
        added.removeAll(old);
        if (!added.isEmpty()) { 
            System.out.println("New nodes detected, replaying messages to: " + added);
            replayTo(added); 
        }
    }

    public void setPartitionMode(boolean enabled) { this.partitionMode = enabled; }
    public boolean isPartitionMode() { return partitionMode; }
    
    public void manualRefreshReplicas() {
        try {
            refreshReplicas();
        } catch (Exception e) {
            System.out.println("Failed to manually refresh replicas: " + e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdownNow();
    }

    private void recordSent(Message m) {
        sentLog.add(m);
        if (sentLog.size() > MAX_LOG_SIZE) {
            sentLog.remove(0);
        }
    }

    public void replayTo(List<String> targets) {
        for (Message m : sentLog) {
            try {
                String json = M.writeValueAsString(m);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> ent = new HttpEntity<>(json, headers);
                for (String r : targets) {
                    try { http.postForObject(r + "/api/messages/replica", ent, String.class); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }

    public void replicate(Message m) {
        try {
            List<String> current = new ArrayList<>(replicas);
            System.out.println("Replicating message " + m.getId() + " to " + current);
            String json = M.writeValueAsString(m);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> ent = new HttpEntity<>(json, headers);
            int acks = 1; // self write
            int need = (current.size() + 1) / 2 + 1; // majority quorum
            for (String r : current) {
                try {
                    ResponseEntity<String> resp = http.postForEntity(r + "/api/messages/replica", ent, String.class);
                    if (resp.getStatusCode().is2xxSuccessful()) {
                        acks++;
                        System.out.println("Ack from replica " + r + " for message " + m.getId());
                    }
                } catch (Exception e) {
                    System.out.println("Failed to replicate message " + m.getId() + " to " + r + ": " + e.getMessage());
                }
            }
            recordSent(m);
            if (!partitionMode && acks < need) {
                System.out.println("Warning: quorum not reached for message " + m.getId() + ". acks=" + acks + "/" + need);
            }
        } catch (Exception e) {
            System.out.println("Error during replication: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcast(Message m) {
        try {
            List<String> current = new ArrayList<>(replicas);
            System.out.println("=== BROADCAST DEBUG ===");
            System.out.println("Broadcasting message " + m.getId() + " to " + current.size() + " replicas: " + current);
            
            if (current.isEmpty()) {
                System.out.println("WARNING: No replicas found! Broadcast will not reach other servers.");
                System.out.println("Make sure other servers are running and registered in ZooKeeper.");
            }
            
            String json = M.writeValueAsString(m);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> ent = new HttpEntity<>(json, headers);
            
            // Send to all other servers
            for (String r : current) {
                try { 
                    System.out.println("Sending broadcast to: " + r);
                    http.postForObject(r + "/api/messages/replica", ent, String.class);
                    System.out.println("Successfully broadcasted message " + m.getId() + " to " + r);
                } catch (Exception e) { 
                    System.out.println("Failed to broadcast message " + m.getId() + " to " + r + ": " + e.getMessage());
                }
            }
            
            // For broadcast messages, also store locally so sender can see it as "SENT"
            // This is handled by the MessageService.handleMessage() method which already stores the message
            
            recordSent(m);
            System.out.println("=== END BROADCAST DEBUG ===");
        } catch (Exception e) {
            System.out.println("Error during broadcast: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void replicateToSingle(Message m, String targetNodeId) {
        try {
            // map from nodeId to URL by reading servers znodes
            ZooKeeper zk = connector.getZooKeeper();
            String serversPath = connector.getServersPath();
            byte[] data = zk.getData(serversPath + "/" + targetNodeId, false, null);
            NodeInfo ni = M.readValue(data, NodeInfo.class);
            String url = "http://" + ni.getHost() + ":" + ni.getPort();
            String json = M.writeValueAsString(m);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> ent = new HttpEntity<>(json, headers);
            http.postForObject(url + "/api/messages/replica", ent, String.class);
            recordSent(m);
            System.out.println("Unicast replicated message " + m.getId() + " to " + targetNodeId + " at " + url);
        } catch (Exception e) {
            System.out.println("Failed unicast replication to " + targetNodeId + ": " + e.getMessage());
        }
    }
}
