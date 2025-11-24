package com.dms.server.controller;

import com.dms.common.constants.Config;
import com.dms.common.model.NodeInfo;
import com.dms.server.zookeeper.ZooKeeperConnector;
import com.dms.server.repository.MessageRepository;
import com.dms.server.service.ReplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ZooKeeperConnector connector;
    private final ObjectMapper M = new ObjectMapper().registerModule(new JavaTimeModule());

    private final MessageRepository messageRepository;
    private final ReplicationService replicationService;

    public AdminController(ZooKeeperConnector connector, MessageRepository messageRepository, ReplicationService replicationService) {
        this.connector = connector;
        this.messageRepository = messageRepository;
        this.replicationService = replicationService;
    }

    @GetMapping("/nodes")
    public ResponseEntity<List<NodeInfo>> nodes() throws Exception {
        ZooKeeper zk = connector.getZooKeeper();
        List<String> children = zk.getChildren(Config.ROOT + "/servers", false);
        List<NodeInfo> out = new ArrayList<>();
        for (String c : children) {
            byte[] data = zk.getData(Config.ROOT + "/servers/" + c, false, null);
            NodeInfo ni = M.readValue(data, NodeInfo.class);
            out.add(ni);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/messages")
    public ResponseEntity<List<com.dms.common.model.Message>> messages() {
        return ResponseEntity.ok(messageRepository.findAll());
    }

    @GetMapping("/heartbeats")
    public ResponseEntity<List<String>> heartbeats() throws Exception {
        ZooKeeper zk = connector.getZooKeeper();
        String hbPath = connector.getHeartbeatsPath();
        List<String> children = zk.getChildren(hbPath, false);
        List<String> beats = new ArrayList<>();
        for (String c : children) {
            byte[] data = zk.getData(hbPath + "/" + c, false, null);
            String ts = new String(data, StandardCharsets.UTF_8);
            beats.add(c + ":" + ts);
        }
        return ResponseEntity.ok(beats.stream().sorted().collect(Collectors.toList()));
    }

    @GetMapping("/leader")
    public ResponseEntity<String> leader() {
        return ResponseEntity.ok(connector.getLeaderId());
    }

    @GetMapping("/replicas")
    public ResponseEntity<List<String>> replicas() {
        // reflect the current in-memory replica targets used for HTTP replication
        try {
            java.lang.reflect.Field f = ReplicationService.class.getDeclaredField("replicas");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> list = new ArrayList<>((List<String>) f.get(replicationService));
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @GetMapping("/partition/enable")
    public ResponseEntity<String> enablePartitionMode() {
        replicationService.setPartitionMode(true);
        return ResponseEntity.ok("partitionMode=enabled");
    }

    @GetMapping("/partition/disable")
    public ResponseEntity<String> disablePartitionMode() {
        replicationService.setPartitionMode(false);
        return ResponseEntity.ok("partitionMode=disabled");
    }

    @GetMapping("/replay")
    public ResponseEntity<String> replay() {
        replicationService.replayTo(new ArrayList<>(List.of())); // no-op placeholder
        return ResponseEntity.ok("replay triggered");
    }

    @GetMapping("/refresh-replicas")
    public ResponseEntity<String> refreshReplicas() {
        replicationService.manualRefreshReplicas();
        return ResponseEntity.ok("Replicas refreshed manually");
    }

    @GetMapping("/trigger-election")
    public ResponseEntity<String> triggerElection() {
        connector.triggerLeaderElection();
        return ResponseEntity.ok("Leader election triggered. Current leader: " + connector.getLeaderId());
    }

    @GetMapping("/test/unicast")
    public ResponseEntity<String> testUnicast(@RequestParam("target") String target) {
        try {
            com.dms.common.model.Message m = new com.dms.common.model.Message("admin", target, "diag-ping");
            replicationService.replicateToSingle(m, target);
            return ResponseEntity.ok("unicast attempted to " + target);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("unicast failed: " + e.getMessage());
        }
    }
}
