package com.dms.client;

import com.dms.client.api.MessageSender;
import com.dms.client.discovery.ZooKeeperDiscovery;
import com.dms.common.constants.Config;
import com.dms.common.model.Message;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CliRunner implements CommandLineRunner {
    private final MessageSender sender;
    private final ZooKeeperDiscovery discovery;

    public CliRunner(MessageSender sender, ZooKeeperDiscovery discovery) {
        this.sender = sender; this.discovery = discovery;
    }

    @Override
    public void run(String... args) throws Exception {
        ZooKeeper zk = new ZooKeeper(Config.ZK_CONNECT, 3000, event -> {});
        String base = discovery.discoverOne(zk);
        if (base == null) { System.out.println("No servers found"); return; }
        Message m = new Message("client1", "server", "hello from client");
        Message resp = sender.send(base, m);
        System.out.println("Sent message, got response id=" + (resp != null ? resp.getId() : "null"));
        zk.close();
    }
}
