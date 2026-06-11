package com.example.demo;

import java.util.*;
import java.util.concurrent.*;

public class HospitalNode {
    private int id;
    private SystemSimulation system;
    private String state = "active";
    private int coordinator = -1;
    private long clock = System.currentTimeMillis();
    private long drift;
    private int[] vectorClock = new int[5];
    private List<Map<String, Object>> donors = new CopyOnWriteArrayList<>();
    private boolean inElection = false;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    public HospitalNode(int id, SystemSimulation system) {
        this.id = id;
        this.system = system;
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
        for (int targetId = 5; targetId > this.id; targetId--) {
            HospitalNode node = system.getNode(targetId);
            if (node != null && "active".equals(node.getState())) {
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

        HospitalNode coordNode = system.getNode(coordinator);
        if (coordNode == null || !"active".equals(coordNode.getState())) {
            system.log("Node " + id + ": Coordinator " + coordinator + " failed! Starting election.");
            startElection();
        }
    }

    public void startElection() {
        if (!"active".equals(state) || inElection) return;
        inElection = true;
        system.log("Node " + id + " starts election");

        boolean higherNodeResponded = false;
        for (int targetId = id + 1; targetId <= 5; targetId++) {
            HospitalNode node = system.getNode(targetId);
            if (node != null && "active".equals(node.getState())) {
                node.receiveElection(id);
                higherNodeResponded = true;
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
        
        for (int i = 1; i <= 5; i++) {
            if (i != id) {
                HospitalNode node = system.getNode(i);
                if (node != null && "active".equals(node.getState())) {
                    node.receiveCoordinator(id);
                }
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
        List<HospitalNode> activeNodes = new ArrayList<>();
        activeNodes.add(this);
        for (int i = 1; i <= 5; i++) {
            if (i != id) {
                HospitalNode node = system.getNode(i);
                if (node != null && "active".equals(node.getState())) {
                    activeNodes.add(node);
                }
            }
        }

        long sum = 0;
        for (HospitalNode n : activeNodes) {
            sum += n.getClock();
        }
        long average = sum / activeNodes.size();

        for (HospitalNode n : activeNodes) {
            long offset = average - n.getClock();
            n.adjustClock(offset);
        }

        system.log("Berkeley synchronization complete.");
        system.broadcastState();
    }

    public void adjustClock(long offset) {
        if (!"active".equals(state)) return;
        this.clock += offset;
        system.log("Node " + id + " adjusted clock by " + offset + " ms");
    }

    public synchronized void addDonor(String name, String bloodType) {
        if (!"active".equals(state)) return;
        
        vectorClock[id - 1]++;
        
        Map<String, Object> donor = new HashMap<>();
        donor.put("id", UUID.randomUUID().toString().substring(0, 8));
        donor.put("name", name);
        donor.put("bloodType", bloodType);
        donor.put("vClock", Arrays.copyOf(vectorClock, 5));
        donor.put("nodeOrigin", id);
        
        donors.add(donor);
        system.log("Node " + id + " added donor " + name + " with VC " + Arrays.toString(vectorClock));
        
        for (int i = 1; i <= 5; i++) {
            if (i != id) {
                HospitalNode node = system.getNode(i);
                if (node != null && "active".equals(node.getState())) {
                    node.receiveDonor(donor, Arrays.copyOf(vectorClock, 5));
                }
            }
        }
        
        system.broadcastState();
    }

    public synchronized void receiveDonor(Map<String, Object> donor, int[] senderClock) {
        if (!"active".equals(state)) return;
        
        for (int i = 0; i < 5; i++) {
            vectorClock[i] = Math.max(vectorClock[i], senderClock[i]);
        }
        
        donors.add(donor);
        donors.sort((a, b) -> {
            int[] vA = (int[]) a.get("vClock");
            int[] vB = (int[]) b.get("vClock");
            boolean aGreater = false;
            boolean bGreater = false;
            for (int i = 0; i < 5; i++) {
                if (vA[i] > vB[i]) aGreater = true;
                if (vA[i] < vB[i]) bGreater = true;
            }
            if (aGreater && !bGreater) return 1;
            if (!aGreater && bGreater) return -1;
            // Desempate obligatorio para cumplir el contrato transitivo estricto de TimSort
            String idA = (String) a.get("id");
            String idB = (String) b.get("id");
            return idA.compareTo(idB);
        });
        
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
