package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/node")
public class PhysicalNodeController {

    @Autowired
    private SystemSimulation systemSimulation;

    @PostMapping("/ping")
    public String ping() {
        return "PONG";
    }

    @PostMapping("/election")
    public String receiveElection(@RequestParam int fromId) {
        HospitalNode myNode = systemSimulation.getLocalNode();
        if (myNode != null) {
            myNode.receiveElection(fromId);
            return "ACK";
        }
        return "FAILED";
    }

    @PostMapping("/coordinator")
    public String receiveCoordinator(@RequestParam int coordId) {
        HospitalNode myNode = systemSimulation.getLocalNode();
        if (myNode != null) {
            myNode.receiveCoordinator(coordId);
            return "ACK";
        }
        return "FAILED";
    }

    @PostMapping("/adjustClock")
    public String adjustClock(@RequestParam long offset) {
        HospitalNode myNode = systemSimulation.getLocalNode();
        if (myNode != null) {
            myNode.adjustClock(offset);
            return "ACK";
        }
        return "FAILED";
    }

    @PostMapping("/receiveDonor")
    public String receiveDonor(@RequestBody Map<String, Object> payload) {
        HospitalNode myNode = systemSimulation.getLocalNode();
        if (myNode != null) {
            Map<String, Object> donor = (Map<String, Object>) payload.get("donor");
            List<Integer> vClockList = (List<Integer>) payload.get("vClock");
            int[] senderClock = vClockList.stream().mapToInt(i -> i).toArray();
            myNode.receiveDonor(donor, senderClock);
            return "ACK";
        }
        return "FAILED";
    }
}
