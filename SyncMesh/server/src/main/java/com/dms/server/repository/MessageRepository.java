package com.dms.server.repository;

import com.dms.common.model.Message;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MessageRepository {
    private final List<Message> store = new ArrayList<>();
    public synchronized void upsertByIdWithLamport(Message incoming) {
        Optional<Integer> existingIdx = Optional.empty();
        for (int i = 0; i < store.size(); i++) {
            if (store.get(i).getId().equals(incoming.getId())) { existingIdx = Optional.of(i); break; }
        }
        if (existingIdx.isEmpty()) {
            store.add(incoming);
            return;
        }
        Message current = store.get(existingIdx.get());
        int cmp = Long.compare(incoming.getLamport(), current.getLamport());
        if (cmp > 0 || (cmp == 0 && safeStr(incoming.getOriginNodeId()).compareTo(safeStr(current.getOriginNodeId())) > 0)) {
            store.set(existingIdx.get(), incoming);
        }
    }

    private String safeStr(String s) { return s == null ? "" : s; }
    public synchronized void save(Message m) { store.add(m); }
    public List<Message> findAll() { return new ArrayList<>(store); }
}
