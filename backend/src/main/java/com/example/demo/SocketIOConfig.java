package com.example.demo;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class SocketIOConfig {

    private SocketIOServer server;

    @Autowired
    private SystemSimulation systemSimulation;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(3001);
        config.setOrigin("*");

        server = new SocketIOServer(config);
        
        systemSimulation.setServer(server);

        server.addConnectListener(client -> {
            System.out.println("Frontend connected");
            systemSimulation.broadcastState();
            client.sendEvent("logs", systemSimulation.getLogs());
        });

        server.start();
        return server;
    }

    @PreDestroy
    public void stopSocketIOServer() {
        if (server != null) {
            server.stop();
        }
    }
}
