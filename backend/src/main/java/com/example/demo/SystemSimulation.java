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
import java.util.concurrent.atomic.AtomicBoolean;
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
            log("Nodo físico " + myId + " iniciado en IP " + myIp);
            localNode.discoverCoordinator();
            localNode.initialTimeSync();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupSocketListeners() {
        server.addEventListener("addDonor", Map.class, (client, data, ackSender) -> {
            Integer targetNodeId = ((Number) data.get("nodeId")).intValue();
            String name = (String) data.get("name");
            String bloodType = (String) data.get("bloodType");

            if (localNode != null && localNode.getId() == targetNodeId) {
                if ("active".equals(localNode.getState())) {
                    localNode.addDonor(name, bloodType);
                } else {
                    log("No se puede agregar donante: El nodo local está desconectado.");
                }
            } else {
                try {
                    String targetIp = nodeIps[targetNodeId - 1].trim();
                    org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
                    rest.postForObject("http://" + targetIp + ":8085/api/node/requestAddDonor?name=" + name + "&bloodType=" + bloodType, null, String.class);
                    log("Creación de donante delegada al Nodo " + targetNodeId + " vía P2P.");
                } catch (Exception e) {
                    log("Error al delegar donante al Nodo " + targetNodeId + ". ¿Está desconectado?");
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
    private Map<Integer, Integer> nodeFailureCount = new ConcurrentHashMap<>();
    private static final int MAX_FAILURES_BEFORE_SKIP = 3;
    private int broadcastCycle = 0;
    private AtomicBoolean broadcasting = new AtomicBoolean(false);

    public void broadcastState() {
        if (server == null || localNode == null) return;
        if (!broadcasting.compareAndSet(false, true)) return;
        try {
        
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
                // Bucle 3: Saltar nodos con fallos consecutivos, reintentar cada 5 ciclos
                int failures = nodeFailureCount.getOrDefault(nodeId, 0);
                if (failures >= MAX_FAILURES_BEFORE_SKIP && broadcastCycle % 5 != 0) {
                    Map<String, Object> offlineMap = new HashMap<>();
                    offlineMap.put("id", nodeId);
                    offlineMap.put("ip", ip);
                    offlineMap.put("state", "failed");
                    offlineMap.put("coordinator", -1);
                    offlineMap.put("clock", System.currentTimeMillis());
                    offlineMap.put("vectorClock", new int[]{0,0,0,0,0});
                    offlineMap.put("donorsCount", 0);
                    nodesData.add(offlineMap);
                    continue;
                }
                try {
                    Map<String, Object> remoteState = rest.getForObject("http://" + ip + ":8085/api/node/state", Map.class);
                    if (remoteState != null && !"offline".equals(remoteState.get("state"))) {
                        remoteState.put("ip", ip);
                        nodesData.add(remoteState);
                        nodeFailureCount.put(nodeId, 0);
                        
                        Boolean wasOnline = nodeOnlineStatus.put(nodeId, true);
                        if (wasOnline != null && !wasOnline) {
                            log("🔌 Nodo " + nodeId + " (IP: " + ip + ") ha reconectado su cable de red.");
                            // Escenario A + Bucle 4: Actualizar coordinador ANTES de sincronizar tiempo
                            int currentCoord = localNode.getCoordinator();
                            if (currentCoord > 0 && currentCoord != localNode.getId()) {
                                try {
                                    String coordIp = nodeIps[currentCoord - 1].trim();
                                    rest.getForObject("http://" + coordIp + ":8085/api/node/state", Map.class);
                                } catch (Exception ex) {
                                    currentCoord = -1;
                                }
                            }
                            if (currentCoord > 0) {
                                rest.postForObject("http://" + ip + ":8085/api/node/coordinator?coordId=" + currentCoord, null, String.class);
                                rest.postForObject("http://" + ip + ":8085/api/node/requestTimeSync", null, String.class);
                            } else {
                                log("Nodo " + nodeId + " reconectó sin coordinador válido. Iniciando elección.");
                                localNode.startElection();
                            }
                        }
                    } else {
                        throw new Exception("Offline");
                    }
                } catch (Exception e) {
                    nodeFailureCount.put(nodeId, nodeFailureCount.getOrDefault(nodeId, 0) + 1);
                    Boolean wasOnline = nodeOnlineStatus.put(nodeId, false);
                    if (wasOnline == null || wasOnline) {
                        log("⚠️ Nodo " + nodeId + " (IP: " + ip + ") ha perdido la conexión (cable desconectado o apagado).");
                    }

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

        broadcastCycle++;

        Map<String, Object> state = new HashMap<>();
        state.put("nodes", nodesData);
        state.put("donors", localNode.getDonors());
        state.put("localNodeId", localNode.getId());
        server.getBroadcastOperations().sendEvent("state", state);
        } finally {
            broadcasting.set(false);
        }
    }

    public List<String> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }
}
