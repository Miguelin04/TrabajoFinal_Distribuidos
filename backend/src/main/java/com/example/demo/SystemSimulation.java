package com.example.demo;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class SystemSimulation {
    
    @Value("${hospital.nodes.ips}")
    private String[] nodeIps;
    
    private HospitalNode localNode;
    private LinkedList<String> logs = new LinkedList<>();
    private SocketIOServer server;

    public SystemSimulation() {}

    public void setServer(SocketIOServer server) {
        this.server = server;
        setupSocketListeners();
    }

    @PostConstruct
    public void init() {
        try {
            String myIp = "Desconocida";
            int myId = -1;
            
            // Buscar la IP real en las tarjetas de red (evitar 127.0.1.1)
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();
                    // Si encontramos una IP que está en nuestra lista de propiedades, la elegimos
                    for (int i = 0; i < nodeIps.length; i++) {
                        if (nodeIps[i].trim().equals(ip)) {
                            myIp = ip;
                            myId = i + 1;
                            break;
                        }
                    }
                }
            }
            
            // Si la IP no coincide, forzamos nodo 1 para pruebas locales
            if (myId == -1) {
                myId = 1;
                System.out.println("No se encontró la IP local en la lista de red. Asumiendo Nodo 1.");
            }
            
            localNode = new HospitalNode(myId, this, nodeIps);
            log("Physical Node " + myId + " initialized on IP " + myIp);
            localNode.discoverCoordinator();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupSocketListeners() {
        server.addEventListener("killNode", Integer.class, (client, data, ackSender) -> {
            if (localNode != null && localNode.getId() == data) {
                log("User requested to kill LOCAL Node " + data);
                localNode.fail();
            }
        });

        server.addEventListener("recoverNode", Integer.class, (client, data, ackSender) -> {
            if (localNode != null && localNode.getId() == data) {
                log("User requested to recover LOCAL Node " + data);
                localNode.recover();
            }
        });

        server.addEventListener("addDonor", Map.class, (client, data, ackSender) -> {
            Integer targetNodeId = ((Number) data.get("nodeId")).intValue();
            String name = (String) data.get("name");
            String bloodType = (String) data.get("bloodType");

            if (localNode != null && localNode.getId() == targetNodeId) {
                if ("active".equals(localNode.getState())) {
                    localNode.addDonor(name, bloodType);
                } else {
                    log("Cannot add donor: Local node is offline.");
                }
            } else {
                try {
                    String targetIp = nodeIps[targetNodeId - 1].trim();
                    org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
                    rest.postForObject("http://" + targetIp + ":8085/api/node/requestAddDonor?name=" + name + "&bloodType=" + bloodType, null, String.class);
                    log("Delegated donor creation to Node " + targetNodeId + " via P2P.");
                } catch (Exception e) {
                    log("Failed to delegate donor creation to Node " + targetNodeId + ". Is it offline?");
                }
            }
        });
    }

    public HospitalNode getLocalNode() {
        return localNode;
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

    private Map<Integer, Boolean> nodeOnlineStatus = new ConcurrentHashMap<>();

    public void broadcastState() {
        if (server == null || localNode == null) return;
        
        List<Map<String, Object>> nodesData = new ArrayList<>();
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(500);
        org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate(factory);

        for (int i = 0; i < nodeIps.length; i++) {
            String ip = nodeIps[i].trim();
            int nodeId = i + 1;
            
            if (nodeId == localNode.getId()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", localNode.getId());
                map.put("ip", ip);
                map.put("state", localNode.getState());
                map.put("coordinator", localNode.getCoordinator());
                map.put("clock", localNode.getClock());
                map.put("vectorClock", localNode.getVectorClock());
                map.put("donorsCount", localNode.getDonorsCount());
                nodesData.add(map);
                nodeOnlineStatus.put(nodeId, true);
            } else {
                try {
                    Map<String, Object> remoteState = rest.getForObject("http://" + ip + ":8085/api/node/state", Map.class);
                    if (remoteState != null && !"offline".equals(remoteState.get("state"))) {
                        remoteState.put("ip", ip);
                        nodesData.add(remoteState);
                        
                        Boolean wasOnline = nodeOnlineStatus.get(nodeId);
                        if (wasOnline != null && !wasOnline) {
                            log("🔌 Nodo " + nodeId + " (IP: " + ip + ") ha reconectado su cable de red.");
                        }
                        nodeOnlineStatus.put(nodeId, true);
                    } else {
                        throw new Exception("Offline");
                    }
                } catch (Exception e) {
                    Boolean wasOnline = nodeOnlineStatus.get(nodeId);
                    if (wasOnline == null || wasOnline) {
                        log("⚠️ Nodo " + nodeId + " (IP: " + ip + ") ha perdido la conexión (cable desconectado o apagado).");
                    }
                    nodeOnlineStatus.put(nodeId, false);

                    Map<String, Object> offlineMap = new HashMap<>();
                    offlineMap.put("id", nodeId);
                    offlineMap.put("ip", ip);
                    offlineMap.put("state", "failed");
                    offlineMap.put("coordinator", -1);
                    offlineMap.put("clock", System.currentTimeMillis());
                    offlineMap.put("vectorClock", new int[]{0,0,0,0,0});
                    offlineMap.put("donorsCount", 0);
                    nodesData.add(offlineMap);
                }
            }
        }

        Map<String, Object> state = new HashMap<>();
        state.put("nodes", nodesData);
        state.put("donors", localNode.getDonors());
        server.getBroadcastOperations().sendEvent("state", state);
    }

    public List<String> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }
}
