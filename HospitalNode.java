import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class HospitalNode {
    private final int id;
    private final int port;
    private static final String HOST = "localhost";
    private static final int TOTAL_NODOS = 5;
    private static final int TIMEOUT_PING = 3000;
    private static final int MAX_INTENTOS_PING = 3;
    private static final int INTERVALO_MONITOREO = 2000;

    private volatile int coordinadorActual;
    private final AtomicBoolean enEleccion = new AtomicBoolean(false);
    private volatile boolean ejecutando = true;
    private ServerSocket serverSocket;

    public HospitalNode(int id) {
        this.id = id;
        this.port = 5000 + id;
        this.coordinadorActual = -1;
    }

    public void iniciar() {
        new Thread(this::escuchar).start();
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        descubrirCoordinador();
        new Thread(this::monitorearCoordinador).start();
    }

    private void escuchar() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[Nodo " + id + "] Escuchando en puerto " + port);
            while (ejecutando) {
                Socket socket = serverSocket.accept();
                new Thread(() -> procesarMensaje(socket)).start();
            }
        } catch (IOException e) {
            if (ejecutando) System.err.println("[Nodo " + id + "] Error en servidor: " + e.getMessage());
        }
    }

    private void descubrirCoordinador() {
        for (int targetId = TOTAL_NODOS; targetId > id; targetId--) {
            try (Socket s = new Socket(HOST, 5000 + targetId)) {
                coordinadorActual = targetId;
                System.out.println("[Nodo " + id + "] Coordinador detectado: Nodo " + targetId);
                return;
            } catch (IOException e) {
                // nodo no disponible, continuar
            }
        }
        if (coordinadorActual == -1) {
            anunciarCoordinador();
        }
    }

    private void procesarMensaje(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String mensaje = in.readLine();
            if (mensaje == null) return;

            System.out.println("[Nodo " + id + "] Recibido: " + mensaje);

            if (mensaje.startsWith("ELECTION:")) {
                int emisorId = Integer.parseInt(mensaje.split(":")[1]);
                out.println("OK");
                System.out.println("[Nodo " + id + "] Enviado OK a Nodo " + emisorId);
                if (emisorId < id) {
                    iniciarEleccion();
                }
            } else if (mensaje.startsWith("COORDINATOR:")) {
                int nuevoCoord = Integer.parseInt(mensaje.split(":")[1]);
                coordinadorActual = nuevoCoord;
                enEleccion.set(false);
                System.out.println("[Nodo " + id + "] Nuevo coordinador: Nodo " + nuevoCoord);
            } else if (mensaje.equals("PING")) {
                out.println("PONG");
            } else if (mensaje.equals("SHUTDOWN")) {
                detener();
            }

        } catch (IOException e) {
            // conexion cerrada
        }
    }

    public void iniciarEleccion() {
        if (!enEleccion.compareAndSet(false, true)) {
            System.out.println("[Nodo " + id + "] Ya hay una eleccion en curso");
            return;
        }

        System.out.println("[Nodo " + id + "] Inicia eleccion");
        boolean respondioSuperior = false;

        for (int targetId = id + 1; targetId <= TOTAL_NODOS; targetId++) {
            try (Socket s = new Socket(HOST, 5000 + targetId);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

                s.setSoTimeout(TIMEOUT_PING);
                out.println("ELECTION:" + id);
                String respuesta = in.readLine();
                if ("OK".equals(respuesta)) {
                    respondioSuperior = true;
                    enEleccion.set(false);
                    System.out.println("[Nodo " + id + "] Nodo " + targetId + " respondio OK");
                    break;
                }
            } catch (IOException e) {
                System.out.println("[Nodo " + id + "] Nodo " + targetId + " no responde");
            }
        }

        if (!respondioSuperior) {
            anunciarCoordinador();
        }
    }

    public void anunciarCoordinador() {
        coordinadorActual = id;
        enEleccion.set(false);
        System.out.println("[Nodo " + id + "] *** SOY EL NUEVO COORDINADOR ***");

        for (int targetId = 1; targetId <= TOTAL_NODOS; targetId++) {
            if (targetId == id) continue;
            try (Socket s = new Socket(HOST, 5000 + targetId);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                out.println("COORDINATOR:" + id);
            } catch (IOException e) {
                System.out.println("[Nodo " + id + "] No se pudo notificar a Nodo " + targetId);
            }
        }
    }

    private void monitorearCoordinador() {
        while (ejecutando) {
            if (coordinadorActual == id) {
                try { Thread.sleep(INTERVALO_MONITOREO); } catch (InterruptedException e) {}
                continue;
            }

            if (coordinadorActual == -1) {
                try { Thread.sleep(INTERVALO_MONITOREO); } catch (InterruptedException e) {}
                continue;
            }

            boolean coordinadorVivo = false;
            int puertoCoord = 5000 + coordinadorActual;

            for (int intento = 1; intento <= MAX_INTENTOS_PING; intento++) {
                try (Socket s = new Socket(HOST, puertoCoord);
                     PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

                    s.setSoTimeout(TIMEOUT_PING);
                    out.println("PING");
                    String respuesta = in.readLine();
                    if ("PONG".equals(respuesta)) {
                        coordinadorVivo = true;
                        break;
                    }
                } catch (IOException e) {
                    System.out.println("[Nodo " + id + "] Ping " + intento + "/" + MAX_INTENTOS_PING +
                            " a coordinador " + coordinadorActual + " fallo");
                }
            }

            if (!coordinadorVivo && ejecutando) {
                System.out.println("[Nodo " + id + "] Coordinador " + coordinadorActual + " caido. Iniciando eleccion...");
                iniciarEleccion();
            }

            try { Thread.sleep(INTERVALO_MONITOREO); } catch (InterruptedException e) {}
        }
    }

    public void detener() {
        ejecutando = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {}
        System.out.println("[Nodo " + id + "] Nodo detenido");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java HospitalNode <id>");
            return;
        }
        int id = Integer.parseInt(args[0]);
        HospitalNode nodo = new HospitalNode(id);
        nodo.iniciar();
    }
}
