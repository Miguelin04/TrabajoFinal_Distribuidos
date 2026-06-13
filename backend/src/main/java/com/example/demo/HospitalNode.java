package com.example.demo;

import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;

public class HospitalNode {
    private int id;
    private SystemSimulation system;
    private String[] nodeIps;
    private String state = "active";
    private int coordinator = -1;
    private long clock = System.currentTimeMillis();
    private int[] vectorClock = new int[5];
    private long clockOffset = 0;
    private List<Map<String, Object>> donors = new CopyOnWriteArrayList<>();
    private boolean inElection = false;
    private long lastTimeSync = 0;
    private RestTemplate restTemplate;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public HospitalNode(int id, SystemSimulation system, String[] nodeIps) {
        this.id = id;
        this.system = system;
        this.nodeIps = nodeIps;

        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(500);
        this.restTemplate = new RestTemplate(factory);

        scheduler.scheduleAtFixedRate(this::tickClock, 1000, 1000, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::monitorCoordinator, 3000, 3000, TimeUnit.MILLISECONDS);
    }

    public void tickClock() {
        if (!"active".equals(state)) return;
        clock = System.currentTimeMillis() + clockOffset;
        system.broadcastState();
    }

    public void discoverCoordinator() {
        if (!"active".equals(state)) return;
        int highestAlive = -1;
        for (int targetId = nodeIps.length; targetId >= 1; targetId--) {
            if (targetId == this.id) continue;
            if (pingNode(targetId) && targetId > highestAlive) {
                highestAlive = targetId;
            }
        }
        if (highestAlive > 0) {
            this.coordinator = highestAlive;
            if (highestAlive < this.id) {
                startElection();
            }
            return;
        }
        this.coordinator = -1;
        announceCoordinator();
    }

    public void monitorCoordinator() {
        if (!"active".equals(state)) return;
        if (coordinator == id || coordinator == -1) return;

        if (!pingNode(coordinator)) {
            system.log("Nodo " + id + ": El coordinador " + coordinator + " falló. Iniciando elección.");
            startElection();
        } else if (coordinator < id) {
            system.log("Nodo " + id + ": El coordinador " + coordinator + " tiene ID menor. Iniciando elección.");
            startElection();
        }
    }

    private boolean pingNode(int targetId) {
        try {
            String ip = nodeIps[targetId - 1].trim();
            String response = restTemplate.postForObject("http://" + ip + ":8085/api/node/ping", null, String.class);
            return "PONG".equals(response);
        } catch (Exception e) {
            return false;
        }
    }

    public void startElection() {
        if (!"active".equals(state) || inElection) return;
        inElection = true;
        system.log("Nodo " + id + " inicia elección");

        boolean higherNodeResponded = false;
        for (int targetId = id + 1; targetId <= nodeIps.length; targetId++) {
            try {
                String ip = nodeIps[targetId - 1].trim();
                String response = restTemplate.postForObject("http://" + ip + ":8085/api/node/election?fromId=" + id, null, String.class);
                if ("ACK".equals(response)) {
                    higherNodeResponded = true;
                }
            } catch (Exception e) {
                // Nodo inactivo
            }
        }

        if (!higherNodeResponded) {
            announceCoordinator();
        } else {
            scheduler.schedule(() -> { inElection = false; }, 5000, TimeUnit.MILLISECONDS);
        }
    }

    public void receiveElection(int fromId) {
        if (!"active".equals(state)) return;
        system.log("Nodo " + id + " recibió mensaje de elección de " + fromId);
        if (id > fromId) {
            startElection();
        }
    }

    public void announceCoordinator() {
        if (!"active".equals(state)) return;
        this.coordinator = id;
        this.inElection = false;
        system.log("*** Nodo " + id + " es el NUEVO COORDINADOR ***");
        
        for (int i = 1; i <= nodeIps.length; i++) {
            if (i != id) {
                try {
                    String ip = nodeIps[i - 1].trim();
                    restTemplate.postForObject("http://" + ip + ":8085/api/node/coordinator?coordId=" + id, null, String.class);
                } catch (Exception e) {}
            }
        }
        system.broadcastState();
    }

    public void receiveCoordinator(int coordId) {
        if (!"active".equals(state)) return;
        if (coordId < this.id) {
            system.log("Nodo " + id + " ignora anuncio de coordinador con ID menor (" + coordId + ")");
            startElection();
            return;
        }
        this.coordinator = coordId;
        this.inElection = false;
        system.log("Nodo " + id + " acepta a Nodo " + coordId + " como coordinador");
        system.broadcastState();
    }

    public void initialTimeSync() {
        if (!"active".equals(state)) return;
        long now = System.currentTimeMillis();
        if (now - lastTimeSync < 5000) return;
        lastTimeSync = now;

        if (coordinator == id) {
            long realTime = System.currentTimeMillis();
            system.log("Nodo " + id + " (Coordinador) inicia sincronización de Cristian con hora real " + realTime);

            OSTimeManager osTime = new OSTimeManager();
            osTime.setLinuxTime(realTime);
            this.clockOffset = 0;
            this.clock = realTime;

            for (int i = 1; i <= nodeIps.length; i++) {
                if (i != id) {
                    try {
                        String ip = nodeIps[i - 1].trim();
                        restTemplate.postForObject(
                            "http://" + ip + ":8085/api/node/adjustClock?serverTime=" + realTime,
                            null, String.class
                        );
                    } catch (Exception e) {
                        system.log("Nodo " + id + ": No se pudo sincronizar Nodo " + i + " — ¿desconectado?");
                    }
                }
            }
            system.log("Sincronización de Cristian completa. Todos los nodos en " + new java.util.Date(realTime));
            system.broadcastState();
        } else {
            if (coordinator != -1) {
                try {
                    String coordIp = nodeIps[coordinator - 1].trim();
                    Map<String, Object> state = restTemplate.getForObject(
                        "http://" + coordIp + ":8085/api/node/state", Map.class);
                    long serverTime = ((Number) state.get("clock")).longValue();
                    syncToServerTime(serverTime);
                } catch (Exception e) {
                    system.log("Nodo " + id + ": No se pudo obtener hora del coordinador Nodo " + coordinator);
                }
            }
        }
    }

    public void syncToServerTime(long serverTime) {
        if (!"active".equals(state)) return;
        clockOffset = serverTime - System.currentTimeMillis();
        this.clock = serverTime;
        system.log("Nodo " + id + " sincronizado a hora del coordinador " + new java.util.Date(serverTime)
            + " (offset " + clockOffset + " ms)");
        new OSTimeManager().setLinuxTime(this.clock);
        this.clockOffset = 0;
    }

    public synchronized void addDonor(String name, String bloodType) {
        if (!"active".equals(state)) return;
        
        vectorClock[id - 1]++;
        
        Map<String, Object> donor = new HashMap<>();
        donor.put("id", UUID.randomUUID().toString().substring(0, 8));
        donor.put("name", name);
        donor.put("bloodType", bloodType);
        
        List<Integer> vClockList = new ArrayList<>();
        for (int v : vectorClock) vClockList.add(v);
        donor.put("vClock", vClockList);
        
        donor.put("nodeOrigin", id);
        
        donors.add(donor);
        system.log("Nodo " + id + " agregó donante " + name + " con RV " + Arrays.toString(vectorClock));
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("donor", donor);
        payload.put("vClock", vClockList);
        
        for (int i = 1; i <= nodeIps.length; i++) {
            if (i != id) {
                try {
                    String ip = nodeIps[i - 1].trim();
                    restTemplate.postForObject("http://" + ip + ":8085/api/node/receiveDonor", payload, String.class);
                } catch (Exception e) {}
            }
        }
        
        system.broadcastState();
    }

    public synchronized void receiveDonor(Map<String, Object> donor, int[] senderClock) {
        if (!"active".equals(state)) return;
        
        for (int i = 0; i < Math.min(vectorClock.length, senderClock.length); i++) {
            vectorClock[i] = Math.max(vectorClock[i], senderClock[i]);
        }
        
        donors.add(donor);
        system.log("Nodo " + id + " recibió donante " + donor.get("name") + ", RV actualizado " + Arrays.toString(vectorClock));
        system.broadcastState();
    }

    public int getId() { return id; }
    public synchronized String getState() { return state; }
    public synchronized int getCoordinator() { return coordinator; }
    public synchronized long getClock() { return clock; }
    public synchronized int[] getVectorClock() { return Arrays.copyOf(vectorClock, 5); }
    public synchronized int getDonorsCount() { return donors.size(); }
    public synchronized List<Map<String, Object>> getDonors() { return new ArrayList<>(donors); }
}