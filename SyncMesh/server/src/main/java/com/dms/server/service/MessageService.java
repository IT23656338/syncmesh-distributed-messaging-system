package com.dms.server.service;

import com.dms.common.model.Message;
import com.dms.server.repository.MessageRepository;
import com.dms.server.zookeeper.ZooKeeperConnector;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MessageService {
    private final MessageRepository repo;
    private final ReplicationService repl;
    private final Set<String> seen = new HashSet<>();
    private final AtomicLong lamportClock = new AtomicLong(0);
    private final ZooKeeperConnector connector;

    public MessageService(MessageRepository repo, ReplicationService repl, ZooKeeperConnector connector) {
        this.repo = repo; this.repl = repl; this.connector = connector;
    }

    public Message handleMessage(Message m) {
        long observed = m.getLamport();
        long next = lamportClock.updateAndGet(curr -> Math.max(curr, observed) + 1);
        m.setLamport(next);
        if (m.getOriginNodeId() == null || m.getOriginNodeId().isEmpty()) {
            m.setOriginNodeId(connector.getNodeId());
        }
        // vector clock merge and increment
        String me = connector.getNodeId();
        m.getVectorClock().put(me, m.getVectorClock().getOrDefault(me, 0L) + 1);
        // Basic validation: receiver must be known unless broadcast
        if (m.getReceiver() == null || m.getReceiver().isEmpty()) {
            throw new IllegalArgumentException("receiver must be provided (node id or BROADCAST)");
        }
        if (!"BROADCAST".equals(m.getReceiver())) {
            try {
                String serversPath = connector.getServersPath();
                if (connector.getZooKeeper().exists(serversPath + "/" + m.getReceiver(), false) == null) {
                    throw new IllegalArgumentException("receiver node not found: " + m.getReceiver());
                }
            } catch (IllegalArgumentException iae) {
                throw iae;
            } catch (Exception e) {
                throw new RuntimeException("failed to validate receiver in ZooKeeper: " + e.getMessage());
            }
        }
        if (seen.contains(m.getId())) {
            System.out.println("Duplicate message ignored: " + m.getId());
            return m;
        }
        seen.add(m.getId());
        repo.upsertByIdWithLamport(m);
        System.out.println("DEBUG: Stored message " + m.getId() + " with originNodeId: " + m.getOriginNodeId() + ", sender: " + m.getSender() + ", receiver: " + m.getReceiver());
        
        // Handle broadcast vs unicast messages
        if ("BROADCAST".equals(m.getReceiver())) {
            repl.broadcast(m);
            System.out.println("Stored and broadcasted message: " + m.getId());
        } else {
            // receiver is a nodeId like server-8082; replicate only to that node
            repl.replicateToSingle(m, m.getReceiver());
            System.out.println("Stored and unicast replicated message: " + m.getId() + " to " + m.getReceiver());
        }
        return m;
    }

    // endpoint used by replicas to accept replicated messages
    public Message acceptReplica(Message m) {
        long observed = m.getLamport();
        lamportClock.updateAndGet(curr -> Math.max(curr, observed) + 1);
        // merge vector clocks
        String me = connector.getNodeId();
        long mine = m.getVectorClock().getOrDefault(me, 0L) + 1;
        m.getVectorClock().put(me, mine);
        if (!seen.contains(m.getId())) {
            repo.upsertByIdWithLamport(m);
            seen.add(m.getId());
            System.out.println("DEBUG: Received replicated message: " + m.getId() + " from " + m.getSender() + " to " + m.getReceiver() + " with originNodeId: " + m.getOriginNodeId());
        } else {
            repo.upsertByIdWithLamport(m);
            System.out.println("DEBUG: Duplicate replicated message considered for conflict resolution: " + m.getId() + " with originNodeId: " + m.getOriginNodeId());
        }
        return m;
    }
}
