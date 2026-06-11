package com.example.demo;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class OSTimeManager {

    // Cambia la hora del sistema operativo en Linux
    public void setLinuxTime(long newTimeMillis) {
        try {
            // Formatear el timestamp a formato aceptado por el comando date de Linux
            // Formato: YYYY-MM-DD HH:MM:SS.mmm
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());
            String formattedDate = formatter.format(Instant.ofEpochMilli(newTimeMillis));

            // Comando a ejecutar: date -s "2026-06-11 18:25:00.123"
            String command = "date -s \"" + formattedDate + "\"";
            
            System.out.println("Ejecutando cambio de hora en OS: " + command);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            Process process = pb.start();
            process.waitFor();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                System.err.println("Error OS Time: " + line);
            }

        } catch (Exception e) {
            System.err.println("Excepción al intentar cambiar la hora del OS en Linux: " + e.getMessage());
        }
    }
}
