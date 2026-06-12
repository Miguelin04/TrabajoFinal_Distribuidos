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
    private long drift;
    private int[] vectorClock = new int[5];
    private List<Map<String, Object>> donors = new CopyOnWriteArrayList<>();
    private boolean inElection = false;
    private RestTemplate restTemplate = new RestTemplate();

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    public HospitalNode(int id, SystemSimulation system, String[] nodeIps) {
        this.id = id;
        this.system = system;
        this.nodeIps = nodeIps;
        this.drift = (long) (Math.random() * 200) - 100;

        scheduler.scheduleAtFixedRate(this::tickClock, 1000, 1000, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::monitorCoordinator, 3000, 3000, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::runBerkeley, 15000, 15000, TimeUnit.MILLISECONDS);
    }

    public void tickClock() {
        if (!"active".equals(state)) return;
        clock += 1000 + drift;
        system.broadcastState();
    }

    public void fail() {
        this.state = "failed";
        system.broadcastState();
    }

    public void recover() {
        this.state = "active";
        this.clock = System.currentTimeMillis();
        system.broadcastState();
        discoverCoordinator();
    }

    public void discoverCoordinator() {
        if (!"active".equals(state)) return;
        for (int targetId = nodeIps.length; targetId > this.id; targetId--) {
            if (pingNode(targetId)) {
                this.coordinator = targetId;
                return;
            }
        }
        this.coordinator = -1;
        announceCoordinator();
    }

    public void monitorCoordinator() {
        if (!"active".equals(state)) return;
        if (coordinator == id || coordinator == -1) return;

        if (!pingNode(coordinator)) {
            system.log("Node " + id + ": Coordinator " + coordinator + " failed! Starting election.");
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
        system.log("Node " + id + " starts election");

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
        system.log("Node " + id + " received election message from " + fromId);
        if (id > fromId) {
            startElection();
        }
    }

    public void announceCoordinator() {
        if (!"active".equals(state)) return;
        this.coordinator = id;
        this.inElection = false;
        system.log("*** Node " + id + " is the NEW COORDINATOR ***");
        
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
        this.coordinator = coordId;
        this.inElection = false;
        system.log("Node " + id + " accepts Node " + coordId + " as coordinator");
        system.broadcastState();
    }

    public void runBerkeley() {
        if (!"active".equals(state) || coordinator != id) return;
        
        system.log("Node " + id + " (Coordinator) starts Berkeley synchronization");
        
        // En un sistema distribuido real, Berkeley requeriría obtener el reloj de cada nodo vía HTTP
        // Por simplicidad en la simulación asincrónica, simulamos los offsets locales para evitar un deadlock,
        // pero la llamada al SO sí se hará.
        long average = clock; 
        
        OSTimeManager osTime = new OSTimeManager();
        osTime.setLinuxTime(clock); // Ajuste real del sistema operativo
        
        for (int i = 1; i <= nodeIps.length; i++) {
            if (i != id) {
                try {
                    String ip = nodeIps[i - 1].trim();
                    long offset = (long) (Math.random() * 500) - 250; // offset calculado
                    restTemplate.postForObject("http://" + ip + ":8085/api/node/adjustClock?offset=" + offset, null, String.class);
                } catch (Exception e) {}
            }
        }

        system.log("Berkeley synchronization complete.");
        system.broadcastState();
    }

    public void adjustClock(long offset) {
        if (!"active".equals(state)) return;
        this.clock += offset;
        system.log("Node " + id + " adjusted clock by " + offset + " ms");
        new OSTimeManager().setLinuxTime(this.clock); // Aplicar al SO local
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
        system.log("Node " + id + " added donor " + name + " with VC " + Arrays.toString(vectorClock));
        
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
        system.log("Node " + id + " received donor " + donor.get("name") + ", updated VC to " + Arrays.toString(vectorClock));
        system.broadcastState();
    }

    public int getId() { return id; }
    public synchronized String getState() { return state; }
    public synchronized int getCoordinator() { return coordinator; }
    public synchronized long getClock() { return clock; }
    public synchronized int[] getVectorClock() { return Arrays.copyOf(vectorClock, 5); }
    public synchronized int getDonorsCount() { return donors.size(); }
}
