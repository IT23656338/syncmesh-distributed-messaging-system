package com.dms.client.discovery;

import com.dms.common.constants.Config;
import com.dms.common.model.NodeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ZooKeeperDiscovery {
    private final ObjectMapper M = new ObjectMapper();

    public String discoverOne(ZooKeeper zk) throws Exception {
        String serversPath = Config.ROOT + "/servers";
        List<String> nodes = zk.getChildren(serversPath, false);
        if (nodes.isEmpty()) return null;
        String first = nodes.get(0);
        byte[] data = zk.getData(serversPath + "/" + first, false, null);
        NodeInfo info = M.readValue(data, NodeInfo.class);
        return "http://" + info.getHost() + ":" + info.getPort();
    }
}
