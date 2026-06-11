import java.io.*;
import java.net.*;

public class KillNode {
    private static final String HOST = "localhost";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java KillNode <id_nodo>");
            System.out.println("Ejemplo: java KillNode 5");
            return;
        }

        int id = Integer.parseInt(args[0]);
        int puerto = 5000 + id;

        try (Socket socket = new Socket(HOST, puerto);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("SHUTDOWN");
            System.out.println("Senial de SHUTDOWN enviada al Nodo " + id);

        } catch (IOException e) {
            System.out.println("Error: No se pudo conectar al Nodo " + id +
                    " en puerto " + puerto + " - " + e.getMessage());
        }
    }
}
