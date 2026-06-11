package com.example.demo;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class SystemSimulation {
    private Map<Integer, HospitalNode> nodes = new ConcurrentHashMap<>();
    private LinkedList<String> logs = new LinkedList<>();
    private SocketIOServer server;

    public SystemSimulation() {}

    public void setServer(SocketIOServer server) {
        this.server = server;
        setupSocketListeners();
    }

    @PostConstruct
    public void init() {
        for (int i = 1; i <= 5; i++) {
            nodes.put(i, new HospitalNode(i, this));
        }
        for (int i = 1; i <= 5; i++) {
            nodes.get(i).discoverCoordinator();
        }
    }

    private void setupSocketListeners() {
        server.addEventListener("killNode", Integer.class, (client, data, ackSender) -> {
            log("User requested to kill Node " + data);
            HospitalNode node = getNode(data);
            if (node != null) node.fail();
        });

        server.addEventListener("recoverNode", Integer.class, (client, data, ackSender) -> {
            log("User requested to recover Node " + data);
            HospitalNode node = getNode(data);
            if (node != null) node.recover();
        });

        server.addEventListener("addDonor", Map.class, (client, data, ackSender) -> {
            Integer nodeId = ((Number) data.get("nodeId")).intValue();
            String name = (String) data.get("name");
            String bloodType = (String) data.get("bloodType");
            
            HospitalNode node = getNode(nodeId);
            if (node != null && "active".equals(node.getState())) {
                node.addDonor(name, bloodType);
            } else {
                log("Cannot add donor: Node " + nodeId + " is inactive.");
            }
        });
    }

    public HospitalNode getNode(int id) {
        return nodes.get(id);
    }

    public void log(String message) {
        String entry = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message;
        System.out.println(entry);
        synchronized (logs) {
            logs.addFirst(entry);
            if (logs.size() > 50) logs.removeLast();
        }
        if (server != null) {
            server.getBroadcastOperations().sendEvent("log", entry);
        }
    }

    public void broadcastState() {
        if (server == null) return;
        List<Map<String, Object>> nodesData = nodes.values().stream().map(n -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", n.getId());
            map.put("state", n.getState());
            map.put("coordinator", n.getCoordinator());
            map.put("clock", n.getClock());
            map.put("vectorClock", n.getVectorClock());
            map.put("donorsCount", n.getDonorsCount());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> state = new HashMap<>();
        state.put("nodes", nodesData);
        server.getBroadcastOperations().sendEvent("state", state);
    }

    public List<String> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }
}
